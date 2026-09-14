package com.example.protaxo.catalog.web;

import com.example.protaxo.catalog.dto.CatalogItemFormData;
import com.example.protaxo.catalog.dto.CatalogItemRequest;
import com.example.protaxo.catalog.dto.CatalogItemResponse;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.service.CatalogItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/catalog-items")
@RequiredArgsConstructor
public class CatalogItemPageController {

    private final CatalogItemService catalogItemService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "MATERIAL") CatalogItemType activeType,
                        @RequestParam(required = false) String filterName,
                        Model model) {
        model.addAttribute("catalogItems", catalogItemService.findAll().stream()
                .filter(ci -> ci.type() == activeType)
                .filter(ci -> isBlank(filterName) || containsIgnoreCase(ci.name(), filterName))
                .toList());
        model.addAttribute("activeType", activeType);
        model.addAttribute("filterName", filterName);
        return "catalog-items/list";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase().contains(search.toLowerCase());
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("catalogItem", catalogItemService.findById(id));
        return "catalog-items/view";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(defaultValue = "MATERIAL") CatalogItemType activeType, Model model) {
        CatalogItemFormData form = new CatalogItemFormData();
        form.setType(activeType);
        model.addAttribute("catalogItem", form);
        addReferenceData(model);
        return "catalog-items/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("catalogItem") CatalogItemFormData form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            return "catalog-items/form";
        }
        catalogItemService.create(toRequest(form));
        return "redirect:/catalog-items?activeType=" + form.getType();
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        CatalogItemResponse response = catalogItemService.findById(id);
        model.addAttribute("catalogItem", toFormData(response));
        model.addAttribute("editId", id);
        addReferenceData(model);
        return "catalog-items/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("catalogItem") CatalogItemFormData form,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editId", id);
            addReferenceData(model);
            return "catalog-items/form";
        }
        catalogItemService.update(id, toRequest(form));
        return "redirect:/catalog-items?activeType=" + form.getType();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        CatalogItemType type = catalogItemService.findById(id).type();
        catalogItemService.softDelete(id);
        return "redirect:/catalog-items?activeType=" + type;
    }

    private void addReferenceData(Model model) {
        model.addAttribute("types", CatalogItemType.values());
    }

    private CatalogItemRequest toRequest(CatalogItemFormData form) {
        return new CatalogItemRequest(form.getType(), form.getName(), form.getBasePrice(), form.getStockQuantity());
    }

    private CatalogItemFormData toFormData(CatalogItemResponse response) {
        CatalogItemFormData form = new CatalogItemFormData();
        form.setType(response.type());
        form.setName(response.name());
        form.setBasePrice(response.basePrice());
        form.setStockQuantity(response.stockQuantity());
        return form;
    }
}
