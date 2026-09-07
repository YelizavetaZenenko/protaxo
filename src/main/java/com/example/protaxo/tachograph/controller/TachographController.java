package com.example.protaxo.tachograph.controller;

import com.example.protaxo.tachograph.dto.TachographRequest;
import com.example.protaxo.tachograph.dto.TachographResponse;
import com.example.protaxo.tachograph.service.TachographService;
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
@RequestMapping("/api/tachographs")
@RequiredArgsConstructor
public class TachographController {

    private final TachographService tachographService;

    @GetMapping
    public List<TachographResponse> findAll() {
        return tachographService.findAll();
    }

    @GetMapping("/{id}")
    public TachographResponse findById(@PathVariable Long id) {
        return tachographService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TachographResponse create(@Valid @RequestBody TachographRequest request) {
        return tachographService.create(request);
    }

    @PutMapping("/{id}")
    public TachographResponse update(@PathVariable Long id, @Valid @RequestBody TachographRequest request) {
        return tachographService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tachographService.softDelete(id);
    }
}
