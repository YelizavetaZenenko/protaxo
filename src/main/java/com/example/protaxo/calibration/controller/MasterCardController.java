package com.example.protaxo.calibration.controller;

import com.example.protaxo.calibration.dto.MasterCardRequest;
import com.example.protaxo.calibration.dto.MasterCardResponse;
import com.example.protaxo.calibration.service.MasterCardService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET is open to any authenticated user (needed to populate the "Номер картки" picker for
 * MASTER too); POST (adding a new card) is restricted to ADMIN — see SecurityConfig, same rule
 * as {@code /api/repair-workers}.
 */
@RestController
@RequestMapping("/api/master-cards")
@RequiredArgsConstructor
public class MasterCardController {

    private final MasterCardService masterCardService;

    @GetMapping
    public List<MasterCardResponse> findAll() {
        return masterCardService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MasterCardResponse create(@Valid @RequestBody MasterCardRequest request) {
        return masterCardService.create(request.cardNumber(), request.holderName());
    }
}
