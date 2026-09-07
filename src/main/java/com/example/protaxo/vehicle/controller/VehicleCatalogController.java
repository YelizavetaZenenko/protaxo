package com.example.protaxo.vehicle.controller;

import com.example.protaxo.vehicle.dto.CatalogNameRequest;
import com.example.protaxo.vehicle.dto.VehicleMakeResponse;
import com.example.protaxo.vehicle.dto.VehicleModelResponse;
import com.example.protaxo.vehicle.service.VehicleCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicle-makes")
@RequiredArgsConstructor
public class VehicleCatalogController {

    private final VehicleCatalogService vehicleCatalogService;

    @GetMapping
    public List<VehicleMakeResponse> findAllMakes() {
        return vehicleCatalogService.findAllMakes();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleMakeResponse createMake(@Valid @RequestBody CatalogNameRequest request) {
        return vehicleCatalogService.createMake(request.name());
    }

    @GetMapping("/{makeId}/models")
    public List<VehicleModelResponse> findModels(@PathVariable Long makeId) {
        return vehicleCatalogService.findModelsByMakeId(makeId);
    }

    @PostMapping("/{makeId}/models")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleModelResponse createModel(@PathVariable Long makeId, @Valid @RequestBody CatalogNameRequest request) {
        return vehicleCatalogService.createModel(makeId, request.name());
    }
}
