package com.example.protaxo.printagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Без Spring-контексту й БД: наклейка малюється в PNG точно під 47,5×81,5мм @ 203dpi. */
class LabelImageRendererTest {

    @Test
    void rendersPureBlackAndWhitePngAtPrinterResolution() throws Exception {
        LabelImageRenderer renderer = new LabelImageRenderer();
        ReflectionTestUtils.setField(renderer, "publicVerifyBaseUrl", "https://vmi3593666.tail3cfdbd.ts.net:8443");

        byte[] png = renderer.renderPng(protocol(), "3124509876");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(380);
        assertThat(image.getHeight()).isEqualTo(652);
        boolean hasBlack = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                assertThat(rgb).isIn(0x000000, 0xFFFFFF);
                hasBlack |= rgb == 0;
            }
        }
        assertThat(hasBlack).isTrue();

        // Зручно глянути очима: target/label-sample.png
        Files.write(Path.of("target", "label-sample.png"), png);
    }

    private static CalibrationProtocolResponse protocol() {
        return new CalibrationProtocolResponse(
                1L, "125", LocalDateTime.of(2026, 9, 30, 10, 0), null, "UA 0042",
                1L, null, null, null, null,
                null, null, null, null, null,
                null, "AA1234BB", "WDB9634031L123456", null, null,
                null, null, null, null, "315/70 R22.5",
                null, "3250", "8000", "8000", null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, "0123456789abcdef0123456789abcdef", null);
    }
}
