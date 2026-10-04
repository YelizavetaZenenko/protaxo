package com.example.printagent;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;

/**
 * Prints a job on a Windows printer. A PNG payload (what the backend sends, see
 * {@code LabelImageRenderer}) is laid out by {@link LabelLayout#compose} and then, per
 * {@link PrintAgentConfig.PrintMode}, either encoded as EZPL/ZPL/EPL and sent raw to the queue, or drawn
 * through the printer's Windows driver. Anything that isn't a PNG goes to the queue as raw bytes.
 */
public final class PrinterClient {

    private static final double POINTS_PER_MM = 72.0 / 25.4;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};

    private PrinterClient() {
    }

    public static void print(byte[] payload, PrintAgentConfig config) throws PrintException {
        if (isPng(payload)) {
            printLabel(decode(payload), config);
        } else {
            printRaw(requireService(config.printerName()), payload);
        }
    }

    public static void printLabel(BufferedImage label, PrintAgentConfig config) throws PrintException {
        PrintService service = requireService(config.printerName());
        BufferedImage canvas = LabelLayout.compose(label, config);
        switch (config.mode()) {
            case EZPL -> printRaw(service, LabelEncoder.ezpl(canvas, config));
            case ZPL -> printRaw(service, LabelEncoder.zpl(canvas, config));
            case EPL -> printRaw(service, LabelEncoder.epl(canvas, config));
            case DRIVER -> printThroughDriver(service, canvas, config);
        }
    }

    public static BufferedImage decode(byte[] png) throws PrintException {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                throw new PrintException("Не вдалось прочитати зображення наклейки");
            }
            return image;
        } catch (IOException e) {
            throw new PrintException("Не вдалось прочитати зображення наклейки: " + e.getMessage());
        }
    }

    public static boolean isPng(byte[] payload) {
        return payload.length >= PNG_SIGNATURE.length
                && Arrays.equals(Arrays.copyOf(payload, PNG_SIGNATURE.length), PNG_SIGNATURE);
    }

    /** Names of the installed printers, for the settings window's drop-down. */
    public static String[] printerNames() {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .toArray(String[]::new);
    }

    /**
     * Asks for a page exactly the label's size with no margins. Java on Windows still snaps it to
     * one of the driver's papers and keeps it portrait, which is why the raw modes exist — this
     * one is kept for a driver that has the label set up as its own paper.
     */
    private static void printThroughDriver(PrintService service, BufferedImage canvas, PrintAgentConfig config)
            throws PrintException {
        double widthPt = config.labelWidthMm() * POINTS_PER_MM;
        double heightPt = config.labelLengthMm() * POINTS_PER_MM;
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(service);
            PageFormat requested = job.defaultPage();
            Paper paper = new Paper();
            paper.setSize(widthPt, heightPt);
            paper.setImageableArea(0, 0, widthPt, heightPt);
            requested.setPaper(paper);
            requested.setOrientation(PageFormat.PORTRAIT);
            PageFormat page = job.validatePage(requested);
            double pageWmm = page.getWidth() / POINTS_PER_MM;
            double pageHmm = page.getHeight() / POINTS_PER_MM;
            if (Math.abs(pageWmm - config.labelWidthMm()) > 2 || Math.abs(pageHmm - config.labelLengthMm()) > 2) {
                System.out.printf("[print-agent] Увага: драйвер дав сторінку %.1f×%.1f мм замість %.1f×%.1f мм —"
                                + " наклейка може обрізатись. Спробуйте спосіб друку EZPL.%n",
                        pageWmm, pageHmm, config.labelWidthMm(), config.labelLengthMm());
            }

            job.setJobName("ProTaxo — наклейка");
            job.setPrintable((graphics, pageFormat, pageIndex) -> {
                if (pageIndex > 0) {
                    return Printable.NO_SUCH_PAGE;
                }
                Graphics2D g = (Graphics2D) graphics;
                // Nearest-neighbour: each canvas pixel is meant to be exactly one printer dot.
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(canvas, 0, 0, (int) Math.round(widthPt), (int) Math.round(heightPt), null);
                return Printable.PAGE_EXISTS;
            }, page);
            job.print();
        } catch (PrinterException e) {
            throw new PrintException("Помилка друку через драйвер: " + e.getMessage());
        }
    }

    /** The spooler hands octet-stream jobs to the port as RAW, past the driver's rendering. */
    private static void printRaw(PrintService service, byte[] bytes) throws PrintException {
        Doc doc = new SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        DocPrintJob job = service.createPrintJob();
        job.print(doc, new HashPrintRequestAttributeSet());
    }

    private static PrintService requireService(String printerName) throws PrintException {
        PrintService service = resolvePrintService(printerName);
        if (service == null) {
            throw new PrintException("Не знайдено принтер" +
                    (printerName != null && !printerName.isBlank() ? " з іменем '" + printerName + "'" : ""));
        }
        return service;
    }

    /**
     * Exact name first, then substring: "Godex G500" must pick that queue, not "Godex G500 #2"
     * (a duplicate Windows makes when the same printer is plugged in again), which a plain
     * substring match could hit first.
     */
    private static PrintService resolvePrintService(String printerName) {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (services.length == 0) {
            return null;
        }
        if (printerName == null || printerName.isBlank()) {
            PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
            return defaultService != null ? defaultService : services[0];
        }
        for (PrintService service : services) {
            if (service.getName().equalsIgnoreCase(printerName.trim())) {
                return service;
            }
        }
        for (PrintService service : services) {
            if (service.getName().toLowerCase().contains(printerName.trim().toLowerCase())) {
                return service;
            }
        }
        return null;
    }
}
