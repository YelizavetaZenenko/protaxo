package com.example.protaxo.calibration.web;

import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import com.example.protaxo.calibration.service.CalibrationProtocolService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The public counterpart of {@link CalibrationProtocolPageController#printPdf} — reachable by
 * anyone with the link (no login), which is exactly the point: this is what the QR code on a
 * printed [[Print Agent]] label encodes, meant to be scanned by an inspector's phone, not a
 * logged-in staff member. Deliberately keyed by {@code qrHash} (an unguessable random token) and
 * NOT by the numeric protocol id — see SecurityConfig, only "/verify/**" is permitAll'd, the
 * regular "/calibration-protocols/**" id-based routes stay behind login, so nobody can enumerate
 * other clients' protocols by walking sequential ids.
 */
@Controller
@RequestMapping("/verify")
@RequiredArgsConstructor
public class PublicVerificationController {

    private final CalibrationProtocolService calibrationProtocolService;
    private final CalibrationProtocolPdfRenderer calibrationProtocolPdfRenderer;

    @GetMapping("/{qrHash}/pdf")
    public void pdf(@PathVariable String qrHash, HttpServletResponse response) throws IOException {
        CalibrationProtocolResponse protocol = calibrationProtocolService.findByQrHash(qrHash);
        byte[] pdf = calibrationProtocolPdfRenderer.render(protocol);

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition", "inline; filename=\"protocol-" + protocol.id() + ".pdf\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }
}
