package com.example.protaxo.pdf.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class PdfRenderService {

    private static final String FONT_FAMILY = "DejaVu Sans";

    /**
     * "Times New Roman" is a Microsoft-licensed font that can't be freely bundled/redistributed
     * off Windows, and the target server OS isn't decided yet (see "Варіанти розгортання
     * (сервер)"). Liberation Serif is metric-compatible and visually near-identical (the same
     * substitute LibreOffice uses) and is SIL Open Font License — safe to ship — so it's
     * registered under the "Times New Roman" family name templates ask for in CSS.
     */
    private static final String TIMES_FONT_FAMILY = "Times New Roman";

    private final TemplateEngine pdfTemplateEngine;

    public byte[] render(String templateName, Context context) {
        String html = pdfTemplateEngine.process(templateName, context);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.useFont(() -> fontStream("DejaVuSans.ttf"), FONT_FAMILY, 400,
                BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> fontStream("DejaVuSans-Bold.ttf"), FONT_FAMILY, 700,
                BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> fontStream("LiberationSerif-Regular.ttf"), TIMES_FONT_FAMILY, 400,
                BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> fontStream("LiberationSerif-Bold.ttf"), TIMES_FONT_FAMILY, 700,
                BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.toStream(out);
        try {
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render PDF for template " + templateName, e);
        }
        return out.toByteArray();
    }

    private InputStream fontStream(String fileName) {
        InputStream stream = getClass().getResourceAsStream("/fonts/" + fileName);
        if (stream == null) {
            throw new IllegalStateException("Font resource not found: " + fileName);
        }
        return stream;
    }
}
