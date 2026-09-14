package com.example.protaxo.calibration.controller;

import com.example.protaxo.calibration.dto.TachographAttributeResponse;
import com.example.protaxo.calibration.entity.TachographAttributeCategory;
import com.example.protaxo.calibration.service.TachographAttributeService;
import com.example.protaxo.vehicle.dto.CatalogNameRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tachograph-attributes")
@RequiredArgsConstructor
public class TachographAttributeController {

    private final TachographAttributeService tachographAttributeService;

    @GetMapping
    public List<TachographAttributeResponse> findByCategory(@RequestParam TachographAttributeCategory category) {
        return tachographAttributeService.findByCategory(category);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TachographAttributeResponse create(@RequestParam TachographAttributeCategory category,
                                               @Valid @RequestBody CatalogNameRequest request) {
        return tachographAttributeService.create(category, request.name());
    }
}
