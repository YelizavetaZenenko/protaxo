package com.example.protaxo.catalog.controller;

import com.example.protaxo.catalog.dto.CatalogItemRequest;
import com.example.protaxo.catalog.dto.CatalogItemResponse;
import com.example.protaxo.catalog.service.CatalogItemService;
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
@RequestMapping("/api/catalog-items")
@RequiredArgsConstructor
public class CatalogItemController {

    private final CatalogItemService catalogItemService;

    @GetMapping
    public List<CatalogItemResponse> findAll() {
        return catalogItemService.findAll();
    }

    @GetMapping("/{id}")
    public CatalogItemResponse findById(@PathVariable Long id) {
        return catalogItemService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse create(@Valid @RequestBody CatalogItemRequest request) {
        return catalogItemService.create(request);
    }

    @PutMapping("/{id}")
    public CatalogItemResponse update(@PathVariable Long id, @Valid @RequestBody CatalogItemRequest request) {
        return catalogItemService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        catalogItemService.softDelete(id);
    }
}
