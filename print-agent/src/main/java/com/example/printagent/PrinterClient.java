package com.example.printagent;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
 * Prints a job on a Windows printer. Two payload kinds:
 *
 * <ul>
 *   <li><b>PNG</b> (what the backend sends since 2026-09-30, see {@code LabelImageRenderer}) — a
 *       finished picture of the label, printed through the printer's own Windows driver like any
 *       image, at its physical size (the image is 8 px/mm, i.e. one pixel per dot on a 203dpi
 *       printer). Works with the Godex G500 driver as installed; no printer command language.</li>
 *   <li>anything else — raw bytes passed straight to the spool queue (the old TSPL path; needs a
 *       "Generic / Text Only" queue, see docs/Print Agent.md).</li>
 * </ul>
 */
public final class PrinterClient {

    private static final double IMAGE_DOTS_PER_MM = 8.0;
    private static final double POINTS_PER_MM = 72.0 / 25.4;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};

    private final String printerName;

    public PrinterClient(String printerName) {
        this.printerName = printerName;
    }

    public void print(byte[] payload) throws PrintException {
        PrintService service = resolvePrintService();
        if (service == null) {
            throw new PrintException("Не знайдено принтер" +
                    (printerName != null && !printerName.isBlank() ? " з іменем '" + printerName + "'" : ""));
        }
        if (isPng(payload)) {
            printImage(service, payload);
        } else {
            printRaw(service, payload);
        }
    }

    private void printImage(PrintService service, byte[] png) throws PrintException {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new PrintException("Не вдалось прочитати зображення наклейки: " + e.getMessage());
        }
        double widthPt = image.getWidth() / IMAGE_DOTS_PER_MM * POINTS_PER_MM;
        double heightPt = image.getHeight() / IMAGE_DOTS_PER_MM * POINTS_PER_MM;

        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(service);
            // The page size comes from the driver's own settings ("Настройки друку" → розмір
            // етикетки) rather than being forced from here — Java maps a custom Paper to the
            // nearest *standard* size on Windows, which for a label roll is worse than wrong.
            PageFormat page = job.defaultPage();
            page.setOrientation(PageFormat.PORTRAIT);
            warnIfPageSizeDiffers(page, widthPt, heightPt);

            job.setJobName("ProTaxo — наклейка");
            job.setPrintable((graphics, pageFormat, pageIndex) -> {
                if (pageIndex > 0) {
                    return Printable.NO_SUCH_PAGE;
                }
                Graphics2D g = (Graphics2D) graphics;
                // Nearest-neighbour: each image pixel is meant to be exactly one printer dot.
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(image, 0, 0, (int) Math.round(widthPt), (int) Math.round(heightPt), null);
                return Printable.PAGE_EXISTS;
            }, page);
            job.print();
        } catch (PrinterException e) {
            throw new PrintException("Помилка друку через драйвер: " + e.getMessage());
        }
    }

    private static void warnIfPageSizeDiffers(PageFormat page, double widthPt, double heightPt) {
        double pageWmm = page.getWidth() / POINTS_PER_MM;
        double pageHmm = page.getHeight() / POINTS_PER_MM;
        double labelWmm = widthPt / POINTS_PER_MM;
        double labelHmm = heightPt / POINTS_PER_MM;
        if (Math.abs(pageWmm - labelWmm) > 2 || Math.abs(pageHmm - labelHmm) > 2) {
            System.out.printf("[print-agent] Увага: у драйвері принтера розмір сторінки %.1f×%.1f мм, а наклейка %.1f×%.1f мм."
                    + " Задайте розмір етикетки в «Настройках друку» принтера.%n", pageWmm, pageHmm, labelWmm, labelHmm);
        }
    }

    private static void printRaw(PrintService service, byte[] bytes) throws PrintException {
        Doc doc = new SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        DocPrintJob job = service.createPrintJob();
        job.print(doc, new HashPrintRequestAttributeSet());
    }

    private static boolean isPng(byte[] payload) {
        if (payload.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (payload[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Exact name first, then substring: "Godex G500" must pick that queue, not "Godex G500 #2"
     * (a duplicate Windows makes when the same printer is plugged in again), which a plain
     * substring match could hit first.
     */
    private PrintService resolvePrintService() {
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
