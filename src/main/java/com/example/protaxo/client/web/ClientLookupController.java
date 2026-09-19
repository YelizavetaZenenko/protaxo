package com.example.protaxo.client.web;

import com.example.protaxo.client.dto.TachographPickerRow;
import com.example.protaxo.driver.dto.DriverResponse;
import com.example.protaxo.driver.service.DriverService;
import com.example.protaxo.tachograph.service.TachographService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Used by the Наряд-заказ form to populate vehicle/driver suggestions scoped to the
 * selected client, via AJAX — see invoices/form.html. Also backs the "Тахограф" picker on
 * [[Протокол калібрування (CalibrationProtocol)]] (`tachographs`).
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientLookupController {

    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final TachographService tachographService;

    @GetMapping("/{id}/vehicles")
    public List<VehicleResponse> vehicles(@PathVariable Long id) {
        return vehicleService.findByClientId(id);
    }

    @GetMapping("/{id}/drivers")
    public List<DriverResponse> drivers(@PathVariable Long id) {
        return driverService.findByClientId(id);
    }

    @GetMapping("/{id}/tachographs")
    public List<TachographPickerRow> tachographs(@PathVariable Long id) {
        Map<Long, VehicleResponse> vehicleById = vehicleService.findByClientId(id).stream()
                .collect(Collectors.toMap(VehicleResponse::id, Function.identity()));
        return tachographService.findByClientId(id).stream()
                .map(t -> new TachographPickerRow(t.id(), t.manufacturer(), t.model(), t.serialNumber(),
                        t.productionDate(), vehicleLabel(vehicleById.get(t.vehicleId()))))
                .toList();
    }

    private String vehicleLabel(VehicleResponse v) {
        if (v == null) {
            return null;
        }
        String makeModel = Stream.of(v.make(), v.model())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        return makeModel.isBlank() ? v.registrationNumber() : v.registrationNumber() + " (" + makeModel + ")";
    }
}
