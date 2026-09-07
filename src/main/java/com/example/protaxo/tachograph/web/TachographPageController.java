package com.example.protaxo.tachograph.web;

import com.example.protaxo.tachograph.dto.TachographFormData;
import com.example.protaxo.tachograph.dto.TachographRequest;
import com.example.protaxo.tachograph.dto.TachographResponse;
import com.example.protaxo.tachograph.service.TachographService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/tachographs")
@RequiredArgsConstructor
public class TachographPageController {

    private final TachographService tachographService;
    private final VehicleService vehicleService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tachographs", tachographService.findAll());
        model.addAttribute("vehicleLabels", vehicleService.findAll().stream()
                .collect(Collectors.toMap(VehicleResponse::id, v -> v.vin() + " (" + v.registrationNumber() + ")", (a, b) -> a)));
        return "tachographs/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("tachograph", new TachographFormData());
        addReferenceData(model);
        return "tachographs/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("tachograph") TachographFormData form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            return "tachographs/form";
        }
        tachographService.create(toRequest(form));
        return "redirect:/tachographs";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        TachographResponse response = tachographService.findById(id);
        model.addAttribute("tachograph", toFormData(response));
        model.addAttribute("editId", id);
        addReferenceData(model);
        return "tachographs/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("tachograph") TachographFormData form,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editId", id);
            addReferenceData(model);
            return "tachographs/form";
        }
        tachographService.update(id, toRequest(form));
        return "redirect:/tachographs";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        tachographService.softDelete(id);
        return "redirect:/tachographs";
    }

    private void addReferenceData(Model model) {
        model.addAttribute("vehicles", vehicleService.findAll());
    }

    private TachographRequest toRequest(TachographFormData form) {
        return new TachographRequest(form.getVehicleId(), form.getManufacturer(), form.getModel(),
                form.getFirmwareVersion(), form.getSerialNumber(), form.getProductionDate());
    }

    private TachographFormData toFormData(TachographResponse response) {
        TachographFormData form = new TachographFormData();
        form.setVehicleId(response.vehicleId());
        form.setManufacturer(response.manufacturer());
        form.setModel(response.model());
        form.setFirmwareVersion(response.firmwareVersion());
        form.setSerialNumber(response.serialNumber());
        form.setProductionDate(response.productionDate());
        return form;
    }
}
