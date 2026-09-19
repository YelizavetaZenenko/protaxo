package com.example.printagent;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;

/**
 * Sends raw TSPL bytes straight to a printer's spool queue via {@code javax.print} — no PDF/GDI
 * rendering involved, the label printer's own firmware interprets the TSPL commands. This only
 * works if the printer is installed in Windows as a "generic / text only" (raw passthrough) print
 * queue rather than a driver that tries to rasterize input — see docs/Print Agent.md.
 *
 * <p>The bytes handed in here are already final — the backend now encodes the Cyrillic
 * {@code TEXT} portions to Windows-1251 itself and embeds the raw logo {@code BITMAP} pixel data
 * inline, all before the job ever leaves the server (see {@code TsplLabelBuilder}). This class used
 * to do that Windows-1251 encoding on a {@code String} payload; it can't anymore because the
 * bitmap bytes aren't valid text in any charset, so the whole job now arrives as a binary
 * WebSocket frame and is passed straight through, unmodified.
 */
public final class PrinterClient {

    private final String printerName;

    public PrinterClient(String printerName) {
        this.printerName = printerName;
    }

    public void print(byte[] tsplCommands) throws PrintException {
        PrintService service = resolvePrintService();
        if (service == null) {
            throw new PrintException("Не знайдено принтер" +
                    (printerName != null && !printerName.isBlank() ? " з іменем '" + printerName + "'" : ""));
        }
        Doc doc = new SimpleDoc(tsplCommands, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        DocPrintJob job = service.createPrintJob();
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        job.print(doc, attributes);
    }

    private PrintService resolvePrintService() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        if (services.length == 0) {
            return null;
        }
        if (printerName == null || printerName.isBlank()) {
            PrintService defaultService = PrintServiceLookup.lookupDefaultPrintService();
            return defaultService != null ? defaultService : services[0];
        }
        for (PrintService service : services) {
            if (service.getName().toLowerCase().contains(printerName.toLowerCase())) {
                return service;
            }
        }
        return null;
    }
}
