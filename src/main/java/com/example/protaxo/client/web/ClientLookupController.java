package com.example.protaxo.client.web;

import com.example.protaxo.driver.dto.DriverResponse;
import com.example.protaxo.driver.service.DriverService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Used by the Наряд-заказ form to populate vehicle/driver suggestions scoped to the
 * selected client, via AJAX — see invoices/form.html.
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientLookupController {

    private final VehicleService vehicleService;
    private final DriverService driverService;

    @GetMapping("/{id}/vehicles")
    public List<VehicleResponse> vehicles(@PathVariable Long id) {
        return vehicleService.findByClientId(id);
    }

    @GetMapping("/{id}/drivers")
    public List<DriverResponse> drivers(@PathVariable Long id) {
        return driverService.findByClientId(id);
    }
}
