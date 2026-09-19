package com.example.protaxo.worker.controller;

import com.example.protaxo.worker.dto.RepairWorkerRequest;
import com.example.protaxo.worker.dto.RepairWorkerResponse;
import com.example.protaxo.worker.service.RepairWorkerService;
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

/**
 * GET is open to any authenticated user (needed to populate the picker on the invoice form
 * for MASTER too); POST (adding a new worker) is restricted to ADMIN — see SecurityConfig.
 */
@RestController
@RequestMapping("/api/repair-workers")
@RequiredArgsConstructor
public class RepairWorkerController {

    private final RepairWorkerService repairWorkerService;

    @GetMapping
    public List<RepairWorkerResponse> findAll() {
        return repairWorkerService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RepairWorkerResponse create(@Valid @RequestBody RepairWorkerRequest request) {
        return repairWorkerService.create(request.fullName(), request.position());
    }

    @PutMapping("/{id}")
    public RepairWorkerResponse update(@PathVariable Long id, @Valid @RequestBody RepairWorkerRequest request) {
        return repairWorkerService.update(id, request.fullName(), request.position());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repairWorkerService.delete(id);
    }
}
