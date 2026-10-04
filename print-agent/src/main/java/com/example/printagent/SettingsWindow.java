package com.example.printagent;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.print.PrintException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * The agent's window: every label setting on the left, a live preview of the label on the roll
 * on the right (built by the same {@link LabelLayout#compose} the printing uses), the agent's log
 * at the bottom. The preview follows the form immediately; "Зберегти" writes printagent.properties
 * and applies the settings to the next job; a test print uses whatever is in the form right now.
 * Closing the window stops the agent.
 */
final class SettingsWindow {

    private static final String SOURCE_SAMPLE = "Зразок наклейки";
    private static final String SOURCE_LAST = "Остання отримана з сервера";
    private static final String SOURCE_FRAME = "Калібрувальна рамка";
    private static final String SOURCE_FILE = "Файл PNG…";

    private final JFrame frame = new JFrame("ProTaxo — друк наклейок");
    private final JLabel status = new JLabel("Сервер: підключаюсь…");
    private final JComboBox<String> printer = new JComboBox<>();
    private final JComboBox<PrintAgentConfig.PrintMode> mode = new JComboBox<>(PrintAgentConfig.PrintMode.values());
    private final JSpinner width = mmSpinner(10, 110);
    private final JSpinner length = mmSpinner(10, 300);
    private final JSpinner gap = mmSpinner(0, 20);
    private final JComboBox<Rotation> rotation = new JComboBox<>(Rotation.values());
    private final JSpinner scale = new JSpinner(new SpinnerNumberModel(100.0, 50.0, 120.0, 1.0));
    private final JSpinner offsetX = mmSpinner(-40, 40);
    private final JSpinner offsetY = mmSpinner(-40, 40);
    private final JSpinner darkness = new JSpinner(new SpinnerNumberModel(0, 0, 15, 1));
    private final JCheckBox invert = new JCheckBox("Поміняти чорне й біле місцями");
    private final JCheckBox confirm = new JCheckBox("Питати перед друком наклейок із сайту");
    private final JTextField serverUrl = new JTextField(28);
    private final JPasswordField token = new JPasswordField(28);
    private final JComboBox<String> source = new JComboBox<>(new String[] {SOURCE_SAMPLE, SOURCE_LAST, SOURCE_FRAME, SOURCE_FILE});
    private final JLabel warning = new JLabel(" ");
    private final JTextArea log = new JTextArea(5, 80);
    private final PreviewPanel preview = new PreviewPanel();
    private final JPanel pendingBar = new JPanel(new BorderLayout(8, 0));
    private final JLabel pendingText = new JLabel();
    /** Labels from the site waiting for «Друкувати» (print.confirm); touched only on the EDT. */
    private final java.util.ArrayDeque<BufferedImage> pending = new java.util.ArrayDeque<>();

    private BufferedImage fileLabel;
    private String lastSource = SOURCE_SAMPLE;
    private boolean connected;

    private SettingsWindow() {
    }

    static void open(Path configPath) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The default look and feel works too.
        }
        new SettingsWindow().build(configPath);
    }

    private void build(Path configPath) {
        teeLogInto(log);
        scale.setEditor(new JSpinner.NumberEditor(scale, "0"));
        scale.setPreferredSize(new Dimension(90, scale.getPreferredSize().height));
        reloadPrinters();
        fillForm(PrintAgentMain.config());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        Row row = new Row(form);
        row.section("Принтер");
        JButton refresh = new JButton("Оновити список");
        refresh.addActionListener(e -> reloadPrinters());
        row.field("Принтер", printer);
        row.field("", refresh);
        row.field("Спосіб друку", mode);
        row.hint("Godex G500 — EZPL. ZPL / EPL — лише з увімкненою в принтері емуляцією; драйвер Windows обрізає широку етикетку.");
        row.field("", confirm);
        row.hint("Наклейка з сайту чекатиме в перегляді кнопки «Друкувати». Діє після «Зберегти».");
        row.section("Етикетка на рулоні");
        row.field("Ширина поперек стрічки, мм", width);
        row.field("Довжина вздовж стрічки, мм", length);
        row.field("Проміжок між етикетками, мм", gap);
        row.section("Розташування наклейки");
        row.field("Поворот", rotation);
        row.field("Масштаб, %", scale);
        row.field("Зсув поперек, мм", offsetX);
        row.field("Зсув вздовж, мм", offsetY);
        row.field("Темність (0 — як у принтері)", darkness);
        row.field("", invert);
        row.section("Сервер");
        row.field("Адреса", serverUrl);
        row.field("Токен", token);
        row.hint("Файл налаштувань: " + configPath);
        JButton save = new JButton("Зберегти");
        save.setFont(save.getFont().deriveFont(Font.BOLD));
        save.addActionListener(e -> save());
        JButton revert = new JButton("Скасувати зміни");
        revert.addActionListener(e -> {
            fillForm(PrintAgentMain.config());
            refreshPreview();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(save);
        buttons.add(Box.gap());
        buttons.add(revert);
        row.field("", buttons);
        row.fill();

        JPanel previewSide = new JPanel(new BorderLayout(0, 6));
        previewSide.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 10));
        JPanel previewTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        previewTop.add(new JLabel("Показати:"));
        previewTop.add(source);
        JButton testPrint = new JButton("Надрукувати пробну");
        testPrint.addActionListener(e -> testPrint());
        previewTop.add(testPrint);
        JButton printPending = new JButton("Друкувати");
        printPending.setFont(printPending.getFont().deriveFont(Font.BOLD));
        printPending.addActionListener(e -> printPending());
        JButton skipPending = new JButton("Пропустити");
        skipPending.addActionListener(e -> skipPending());
        pendingBar.setBackground(new Color(0xFFF4CE));
        pendingBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE0B000)), BorderFactory.createEmptyBorder(4, 8, 4, 4)));
        pendingText.setFont(pendingText.getFont().deriveFont(Font.BOLD));
        JPanel pendingButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        pendingButtons.setOpaque(false);
        pendingButtons.add(printPending);
        pendingButtons.add(skipPending);
        pendingBar.add(pendingText, BorderLayout.CENTER);
        pendingBar.add(pendingButtons, BorderLayout.EAST);
        pendingBar.setVisible(false);
        JPanel previewHeader = new JPanel(new BorderLayout(0, 6));
        previewHeader.add(previewTop, BorderLayout.NORTH);
        previewHeader.add(pendingBar, BorderLayout.SOUTH);
        previewSide.add(previewHeader, BorderLayout.NORTH);
        previewSide.add(preview, BorderLayout.CENTER);
        warning.setForeground(new Color(0xB3261E));
        previewSide.add(warning, BorderLayout.SOUTH);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formScroll, previewSide);
        // Wide enough for the form plus a vertical scrollbar, so it never needs a horizontal one.
        top.setDividerLocation(form.getPreferredSize().width + formScroll.getVerticalScrollBar().getPreferredSize().width + 4);
        top.setResizeWeight(0.35);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setMinimumSize(new Dimension(0, 80));
        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, logScroll);
        main.setResizeWeight(1.0);

        status.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        status.setFont(status.getFont().deriveFont(Font.BOLD));
        frame.getContentPane().add(status, BorderLayout.NORTH);
        frame.getContentPane().add(main, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        // Fit the screen: the laptop runs at 125% scaling, where a fixed 1150 px window overflows it.
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(Math.min(1150, screen.width), Math.min(780, screen.height));
        frame.setLocationRelativeTo(null);
        if (screen.width < 1150 || screen.height < 780) {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        Runnable onChange = this::refreshPreview;
        for (JSpinner s : new JSpinner[] {width, length, gap, scale, offsetX, offsetY, darkness}) {
            s.addChangeListener(e -> onChange.run());
        }
        mode.addActionListener(e -> onChange.run());
        rotation.addActionListener(e -> onChange.run());
        invert.addActionListener(e -> onChange.run());
        source.addActionListener(e -> onSourceChanged());

        PrintAgentMain.onConfirmNeeded(payload -> SwingUtilities.invokeLater(() -> addPending(payload)));
        PrintAgentMain.onStatus(connected -> SwingUtilities.invokeLater(() -> setConnected(connected)));
        PrintAgentMain.onLabelReceived(() -> SwingUtilities.invokeLater(() -> {
            if (SOURCE_LAST.equals(source.getSelectedItem())) {
                refreshPreview();
            }
        }));

        refreshPreview();
        frame.setVisible(true);
    }

    private void fillForm(PrintAgentConfig c) {
        printer.setSelectedItem(c.printerName());
        mode.setSelectedItem(c.mode());
        width.setValue(c.labelWidthMm());
        length.setValue(c.labelLengthMm());
        gap.setValue(c.labelGapMm());
        rotation.setSelectedItem(Rotation.of(c.labelRotation()));
        scale.setValue(c.scalePercent());
        offsetX.setValue(c.offsetXmm());
        offsetY.setValue(c.offsetYmm());
        darkness.setValue(c.darkness());
        invert.setSelected(c.invert());
        confirm.setSelected(c.confirmBeforePrint());
        serverUrl.setText(c.backendWsUrl());
        token.setText(c.token());
    }

    /** The settings exactly as typed — what the preview shows and a test print uses, saved or not. */
    private PrintAgentConfig formConfig() {
        Object printerName = printer.getSelectedItem();
        return new PrintAgentConfig(
                serverUrl.getText().trim(),
                new String(token.getPassword()).trim(),
                printerName == null ? "" : printerName.toString().trim(),
                (PrintAgentConfig.PrintMode) mode.getSelectedItem(),
                ((Number) width.getValue()).doubleValue(),
                ((Number) length.getValue()).doubleValue(),
                ((Number) gap.getValue()).doubleValue(),
                ((Rotation) rotation.getSelectedItem()).degrees,
                ((Number) scale.getValue()).doubleValue(),
                ((Number) offsetX.getValue()).doubleValue(),
                ((Number) offsetY.getValue()).doubleValue(),
                ((Number) darkness.getValue()).intValue(),
                invert.isSelected(),
                confirm.isSelected());
    }

    private void save() {
        try {
            PrintAgentMain.applyConfig(formConfig());
            setConnected(connected);
            System.out.println("[print-agent] Налаштування збережено — діють з наступної наклейки.");
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(frame, "Не вдалось зберегти: " + e.getMessage(), "Помилка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void testPrint() {
        BufferedImage label = currentLabel();
        if (label == null) {
            return;
        }
        PrintAgentConfig c = formConfig();
        int answer = JOptionPane.showConfirmDialog(frame,
                "Надрукувати одну наклейку («" + source.getSelectedItem() + "») на «" + c.printerName() + "»\n"
                        + "з налаштуваннями, що зараз у формі (" + c.mode().key().toUpperCase() + ")?",
                "Пробний друк", JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        new Thread(() -> {
            try {
                PrinterClient.printLabel(label, c);
                System.out.println("[print-agent] Пробну наклейку надіслано на друк (" + c.mode().key() + ")");
            } catch (PrintException e) {
                System.err.println("[print-agent] Помилка пробного друку: " + e.getMessage());
            }
        }, "test-print").start();
    }

    private void addPending(byte[] payload) {
        try {
            pending.add(PrinterClient.decode(payload));
        } catch (PrintException e) {
            System.err.println("[print-agent] " + e.getMessage());
            return;
        }
        updatePending();
        // Bring the window up so the waiting label isn't missed behind other programs.
        frame.setExtendedState(frame.getExtendedState() & ~JFrame.ICONIFIED);
        frame.toFront();
        java.awt.Toolkit.getDefaultToolkit().beep();
    }

    /** Prints the waiting label with the settings as they are in the form now, saved or not. */
    private void printPending() {
        BufferedImage label = pending.poll();
        if (label == null) {
            return;
        }
        PrintAgentConfig c = formConfig();
        updatePending();
        new Thread(() -> {
            try {
                PrinterClient.printLabel(label, c);
                System.out.println("[print-agent] Наклейку з сайту надіслано на друк (" + c.mode().key() + ")");
            } catch (PrintException e) {
                System.err.println("[print-agent] Помилка друку: " + e.getMessage());
            }
        }, "confirmed-print").start();
    }

    private void skipPending() {
        if (pending.poll() != null) {
            System.out.println("[print-agent] Наклейку з сайту пропущено (не друкувалась)");
        }
        updatePending();
    }

    private void updatePending() {
        pendingBar.setVisible(!pending.isEmpty());
        int more = pending.size() - 1;
        pendingText.setText("Наклейка з сайту чекає друку" + (more > 0 ? " (ще в черзі: " + more + ")" : ""));
        refreshPreview();
    }

    private void onSourceChanged() {
        if (SOURCE_FILE.equals(source.getSelectedItem())) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Зображення PNG", "png"));
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                try {
                    fileLabel = ImageIO.read(chooser.getSelectedFile());
                } catch (IOException e) {
                    fileLabel = null;
                }
            }
            if (fileLabel == null) {
                source.setSelectedItem(lastSource);
                return;
            }
        }
        lastSource = (String) source.getSelectedItem();
        refreshPreview();
    }

    /** Null (with the reason shown under the preview) when the chosen source has nothing yet. */
    private BufferedImage currentLabel() {
        if (!pending.isEmpty()) {
            return pending.peek();
        }
        Object chosen = source.getSelectedItem();
        try {
            if (SOURCE_LAST.equals(chosen)) {
                Path last = PrintAgentMain.lastLabelPath();
                if (!Files.exists(last)) {
                    warning.setText("З сервера ще не приходило жодної наклейки — оберіть «Зразок наклейки».");
                    return null;
                }
                return ImageIO.read(last.toFile());
            }
            if (SOURCE_FRAME.equals(chosen)) {
                return LabelLayout.calibrationPattern(formConfig());
            }
            if (SOURCE_FILE.equals(chosen)) {
                return fileLabel;
            }
            try (InputStream in = SettingsWindow.class.getResourceAsStream("/sample-label.png")) {
                return in == null ? null : ImageIO.read(in);
            }
        } catch (IOException e) {
            warning.setText("Не вдалось прочитати наклейку: " + e.getMessage());
            return null;
        }
    }

    private void refreshPreview() {
        warning.setText(" ");
        BufferedImage label = currentLabel();
        PrintAgentConfig c = formConfig();
        preview.show(label, c);
        if (label == null) {
            return;
        }
        double[] turned = LabelLayout.turnedSizeMm(label, c);
        boolean overflows = turned[0] + Math.abs(c.offsetXmm()) * 2 > c.labelWidthMm() + 0.5
                || turned[1] + Math.abs(c.offsetYmm()) * 2 > c.labelLengthMm() + 0.5;
        if (overflows) {
            warning.setText(String.format("Наклейка після повороту %.1f×%.1f мм не вміщається в етикетку %.1f×%.1f мм —"
                            + " червона рамка показує, що обріжеться. Змініть поворот або розмір.",
                    turned[0], turned[1], c.labelWidthMm(), c.labelLengthMm()));
        }
    }

    private void reloadPrinters() {
        Object selected = printer.getSelectedItem();
        printer.removeAllItems();
        for (String name : PrinterClient.printerNames()) {
            printer.addItem(name);
        }
        printer.setEditable(true);
        if (selected != null) {
            printer.setSelectedItem(selected);
        }
    }

    private void setConnected(boolean connected) {
        this.connected = connected;
        status.setText(connected ? "Сервер: підключено — наклейки з сайту " + (PrintAgentMain.config().confirmBeforePrint()
                        ? "чекатимуть кнопки «Друкувати»" : "друкуватимуться автоматично")
                : "Сервер: немає з'єднання (перевірте Tailscale, адресу й токен) — пробую знову…");
        status.setForeground(connected ? new Color(0x1E7A34) : new Color(0xB3261E));
    }

    private static JSpinner mmSpinner(double min, double max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(Math.max(min, 0.0), min, max, 0.5));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.0"));
        spinner.setPreferredSize(new Dimension(90, spinner.getPreferredSize().height));
        return spinner;
    }

    /** Copies everything the agent prints into the window's log, keeping the console output too. */
    private static void teeLogInto(JTextArea area) {
        System.setOut(new PrintStream(new Tee(System.out, area), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new Tee(System.err, area), true, StandardCharsets.UTF_8));
    }

    private static final class Tee extends OutputStream {
        private final PrintStream original;
        private final JTextArea area;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        Tee(PrintStream original, JTextArea area) {
            this.original = original;
            this.area = area;
        }

        @Override
        public synchronized void write(int b) {
            original.write(b);
            line.write(b);
            if (b == '\n') {
                String text = line.toString(StandardCharsets.UTF_8);
                line.reset();
                SwingUtilities.invokeLater(() -> {
                    area.append(text);
                    area.setCaretPosition(area.getDocument().getLength());
                });
            }
        }

        @Override
        public void flush() {
            original.flush();
        }
    }

    private enum Rotation {
        R0(0, "0° — як є"),
        R90(90, "90° за годинниковою"),
        R180(180, "180° — догори дриґом"),
        R270(270, "270° (90° проти годинникової)");

        final int degrees;
        private final String title;

        Rotation(int degrees, String title) {
            this.degrees = degrees;
            this.title = title;
        }

        static Rotation of(int degrees) {
            for (Rotation r : values()) {
                if (r.degrees == degrees) {
                    return r;
                }
            }
            return R0;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /** Two-column form builder: label on the left, control on the right, grey section titles. */
    private static final class Row {
        private final JPanel panel;
        private int y;

        Row(JPanel panel) {
            this.panel = panel;
        }

        void section(String title) {
            JLabel label = new JLabel(title);
            label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 1f));
            GridBagConstraints c = base(0);
            c.gridwidth = 2;
            c.insets = new Insets(y == 0 ? 0 : 14, 0, 4, 0);
            panel.add(label, c);
            y++;
        }

        void field(String title, JComponent control) {
            panel.add(new JLabel(title), base(0));
            GridBagConstraints c = base(1);
            c.fill = GridBagConstraints.NONE;
            panel.add(control, c);
            y++;
        }

        void hint(String text) {
            JLabel label = new JLabel("<html><div style='width:300px;color:#666'>" + text + "</div></html>");
            GridBagConstraints c = base(0);
            c.gridwidth = 2;
            panel.add(label, c);
            y++;
        }

        void fill() {
            GridBagConstraints c = base(0);
            c.weighty = 1;
            panel.add(new JPanel(), c);
        }

        private GridBagConstraints base(int x) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = x;
            c.gridy = y;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(2, 0, 2, 8);
            return c;
        }
    }

    private static final class Box {
        static JComponent gap() {
            JPanel p = new JPanel();
            p.setPreferredSize(new Dimension(8, 1));
            return p;
        }
    }

    /**
     * The roll seen from above, feed running top to bottom: the previous and next labels as
     * outlines, the current one with the composed canvas on it pixel for pixel, a red dashed frame
     * where the picture would reach if it weren't cropped by the label edge.
     */
    private static final class PreviewPanel extends JPanel {
        private BufferedImage label;
        private PrintAgentConfig config;

        PreviewPanel() {
            setBackground(new Color(0xE9E7E2));
            setPreferredSize(new Dimension(600, 500));
        }

        void show(BufferedImage label, PrintAgentConfig config) {
            this.label = label;
            this.config = config;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (config == null) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            double wMm = config.labelWidthMm();
            double lMm = config.labelLengthMm();
            double gapMm = config.labelGapMm();
            double linerMm = 2.5;
            double viewH = lMm + 2 * (gapMm + lMm * 0.35) + 24;
            // Room in pixels for the rotated length on the left and the feed arrow's caption on the right.
            g.setFont(getFont().deriveFont(11f));
            int captionPx = g.getFontMetrics().stringWidth("друкується першим");
            int leftPx = 30;
            int rightPx = 18 + 10 + captionPx + 12;
            double scale = Math.min((getWidth() - leftPx - rightPx) / (wMm + 2 * linerMm), getHeight() / viewH);
            double cx = leftPx + (getWidth() - leftPx - rightPx) / 2.0;
            double labelX = cx - wMm * scale / 2;
            double labelY = (getHeight() - lMm * scale) / 2;

            // Liner (backing strip) running through the printer.
            g.setColor(new Color(0xF7F5EF));
            g.fillRect((int) (labelX - linerMm * scale), 0, (int) ((wMm + 2 * linerMm) * scale), getHeight());
            g.setColor(new Color(0xC9C5BB));
            g.drawRect((int) (labelX - linerMm * scale), -1, (int) ((wMm + 2 * linerMm) * scale), getHeight() + 2);

            // Neighbouring labels, so the gap and the feed direction are obvious.
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] {4f, 4f}, 0f));
            g.setColor(new Color(0xB5B0A4));
            double step = (lMm + gapMm) * scale;
            for (int i : new int[] {-1, 1}) {
                g.drawRoundRect((int) labelX, (int) (labelY + i * step), (int) (wMm * scale), (int) (lMm * scale), 10, 10);
            }

            // The label itself.
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(Color.WHITE);
            g.fillRoundRect((int) labelX, (int) labelY, (int) (wMm * scale), (int) (lMm * scale), 10, 10);
            if (label != null) {
                BufferedImage canvas = LabelLayout.compose(label, config);
                if (config.invert()) {
                    canvas = inverted(canvas);
                }
                Graphics2D gi = (Graphics2D) g.create();
                gi.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        scale / LabelLayout.DOTS_PER_MM >= 1 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                gi.drawImage(canvas, (int) labelX, (int) labelY, (int) (wMm * scale), (int) (lMm * scale), null);
                gi.dispose();

                double[] turned = LabelLayout.turnedSizeMm(label, config);
                double px = labelX + ((wMm - turned[0]) / 2 + config.offsetXmm()) * scale;
                double py = labelY + ((lMm - turned[1]) / 2 + config.offsetYmm()) * scale;
                if (px < labelX - 1 || py < labelY - 1
                        || px + turned[0] * scale > labelX + wMm * scale + 1
                        || py + turned[1] * scale > labelY + lMm * scale + 1) {
                    g.setColor(new Color(0xD93025));
                    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {6f, 4f}, 0f));
                    g.drawRect((int) px, (int) py, (int) (turned[0] * scale), (int) (turned[1] * scale));
                }
            }
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(new Color(0x6B6659));
            g.drawRoundRect((int) labelX, (int) labelY, (int) (wMm * scale), (int) (lMm * scale), 10, 10);

            // Dimensions and the feed arrow.
            g.setFont(getFont().deriveFont(12f));
            FontMetrics fm = g.getFontMetrics();
            String across = String.format("%.1f мм поперек стрічки", wMm);
            g.setColor(new Color(0x3C3A33));
            g.drawString(across, (int) (cx - fm.stringWidth(across) / 2.0), (int) (labelY - 6));
            String along = String.format("%.1f мм", lMm);
            AffineTransform saved = g.getTransform();
            g.rotate(-Math.PI / 2, labelX - linerMm * scale - 8, labelY + lMm * scale / 2);
            g.drawString(along, (int) (labelX - linerMm * scale - 8 - fm.stringWidth(along) / 2.0), (int) (labelY + lMm * scale / 2));
            g.setTransform(saved);

            int ax = (int) (labelX + (wMm + linerMm) * scale + 18);
            int top = (int) labelY;
            int bottom = (int) (labelY + lMm * scale);
            g.setColor(new Color(0x6B6659));
            g.setStroke(new BasicStroke(2f));
            g.drawLine(ax, bottom, ax, top + 10);
            g.fillPolygon(new int[] {ax, ax - 6, ax + 6}, new int[] {top, top + 12, top + 12}, 3);
            g.setFont(getFont().deriveFont(11f));
            g.drawString("цей край", ax + 10, top + 10);
            g.drawString("друкується першим", ax + 10, top + 24);
            g.dispose();
        }

        private static BufferedImage inverted(BufferedImage src) {
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    out.setRGB(x, y, LabelLayout.isBlack(src.getRGB(x, y)) ? 0xFFFFFF : 0x000000);
                }
            }
            return out;
        }
    }
}
