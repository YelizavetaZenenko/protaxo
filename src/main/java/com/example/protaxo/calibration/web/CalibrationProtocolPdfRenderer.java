package com.example.protaxo.calibration.web;

import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.pdf.service.PdfRenderService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

/**
 * Builds the PDF of a calibration protocol — shared by the staff print route and the public QR
 * route so both always print exactly the same document.
 */
@Component
@RequiredArgsConstructor
public class CalibrationProtocolPdfRenderer {

    private final ClientService clientService;
    private final VehicleService vehicleService;
    private final PdfRenderService pdfRenderService;

    public byte[] render(CalibrationProtocolResponse protocol) {
        ClientResponse client = clientService.findById(protocol.clientId());
        Optional<VehicleResponse> vehicle = findVehicle(protocol);

        Context context = new Context();
        context.setVariable("protocol", protocol);
        context.setVariable("clientName", client.name());
        // п. 7: код ЄДРПОУ для юросіб, інакше ІПН
        context.setVariable("clientCode", isBlank(client.edrpou()) ? client.code() : client.edrpou());
        // п. 9.2 / 9.3: з довідника авто контрагента; без нього — назва з протоколу без номера
        context.setVariable("vehicleMakeModel", vehicle
                .map(v -> Stream.of(v.make(), v.model()).filter(s -> !isBlank(s)).collect(Collectors.joining(" ")))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> makeModelFromName(protocol.vehicleName(), protocol.vehicleVrn())));
        context.setVariable("vehicleYear", vehicle.map(VehicleResponse::year).orElse(null));
        context.setVariable("logoDataUri", pdfRenderService.classpathImageDataUri("images/logo.png", "image/png"));
        context.setVariable("watermarkDataUri", pdfRenderService.watermarkPageDataUri("images/logo.png"));
        return pdfRenderService.render("calibration-protocol", context);
    }

    private Optional<VehicleResponse> findVehicle(CalibrationProtocolResponse protocol) {
        return vehicleService.findByClientId(protocol.clientId()).stream()
                .filter(v -> sameText(v.vin(), protocol.vehicleVin()) || sameText(v.registrationNumber(), protocol.vehicleVrn()))
                .findFirst();
    }

    // "AA1234BB (DAF XF)" → "DAF XF"; будь-яка інша назва — як є
    private static String makeModelFromName(String name, String vrn) {
        if (isBlank(name)) {
            return null;
        }
        String trimmed = name.trim();
        if (!isBlank(vrn) && trimmed.startsWith(vrn.trim() + " (") && trimmed.endsWith(")")) {
            return trimmed.substring(vrn.trim().length() + 2, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean sameText(String a, String b) {
        return !isBlank(a) && !isBlank(b) && Objects.equals(a.trim().toUpperCase(), b.trim().toUpperCase());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
