package com.example.protaxo.calibration.controller;

import com.example.protaxo.calibration.dto.CalibrationProtocolRequest;
import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import com.example.protaxo.calibration.service.CalibrationProtocolService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calibration-protocols")
@RequiredArgsConstructor
public class CalibrationProtocolController {

    private final CalibrationProtocolService calibrationProtocolService;

    @GetMapping
    public List<CalibrationProtocolResponse> findAll() {
        return calibrationProtocolService.findAll();
    }

    @GetMapping("/{id}")
    public CalibrationProtocolResponse findById(@PathVariable Long id) {
        return calibrationProtocolService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalibrationProtocolResponse create(@Valid @RequestBody CalibrationProtocolRequest request) {
        return calibrationProtocolService.create(request);
    }

    @PutMapping("/{id}")
    public CalibrationProtocolResponse update(@PathVariable Long id, @Valid @RequestBody CalibrationProtocolRequest request) {
        return calibrationProtocolService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        calibrationProtocolService.softDelete(id);
    }
}
