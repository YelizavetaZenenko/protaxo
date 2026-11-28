package com.example.protaxo.client.web;

import com.example.protaxo.client.entity.CustomLegalForm;
import com.example.protaxo.client.service.CustomLegalFormService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Свої форми власності для форми контрагента: список і «+ Додати свою форму» з модального вікна. */
@RestController
@RequestMapping("/api/legal-forms")
@RequiredArgsConstructor
public class CustomLegalFormController {

    private final CustomLegalFormService service;

    public record LegalFormRequest(String shortLabel, String fullLabel) {
    }

    public record LegalFormResponse(Long id, String code, String shortLabel, String fullLabel) {
        static LegalFormResponse of(CustomLegalForm form) {
            return new LegalFormResponse(form.getId(), ClientPageController.customCode(form.getId()),
                    form.getShortLabel(), form.fullOrShortLabel());
        }
    }

    @GetMapping
    public List<LegalFormResponse> list() {
        return service.findAll().stream().map(LegalFormResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LegalFormResponse create(@RequestBody LegalFormRequest request) {
        return LegalFormResponse.of(service.create(request.shortLabel(), request.fullLabel()));
    }
}
