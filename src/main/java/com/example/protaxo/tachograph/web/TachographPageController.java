package com.example.protaxo.tachograph.web;

import com.example.protaxo.tachograph.dto.TachographFormData;
import com.example.protaxo.tachograph.dto.TachographRequest;
import com.example.protaxo.tachograph.dto.TachographResponse;
import com.example.protaxo.tachograph.service.TachographService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
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

@Controller
@RequestMapping("/tachographs")
@RequiredArgsConstructor
public class TachographPageController {

    private final TachographService tachographService;
    private final VehicleService vehicleService;

    @GetMapping
    public String list() {
        // Standalone list retired — tachographs now live as a tab on /vehicles, since each one
        // is tied to a single specific car (see [[Автомобілі]]).
        return "redirect:/vehicles?tab=tachographs";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        TachographResponse tachograph = tachographService.findById(id);
        VehicleResponse vehicle = vehicleService.findById(tachograph.vehicleId());
        model.addAttribute("tachograph", tachograph);
        model.addAttribute("vehicleLabel", vehicle.vin() + " (" + vehicle.registrationNumber() + ")");
        model.addAttribute("vehicleId", vehicle.id());
        return "tachographs/view";
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
        return "redirect:/vehicles?tab=tachographs";
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
        return "redirect:/vehicles?tab=tachographs";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        tachographService.softDelete(id);
        return "redirect:/vehicles?tab=tachographs";
    }

    private void addReferenceData(Model model) {
        model.addAttribute("vehicles", vehicleService.findAll());
    }

    private TachographRequest toRequest(TachographFormData form) {
        return new TachographRequest(form.getVehicleId(), form.getManufacturer(), form.getModel(),
                form.getSerialNumber(), form.getProductionDate());
    }

    private TachographFormData toFormData(TachographResponse response) {
        TachographFormData form = new TachographFormData();
        form.setVehicleId(response.vehicleId());
        form.setManufacturer(response.manufacturer());
        form.setModel(response.model());
        form.setSerialNumber(response.serialNumber());
        form.setProductionDate(response.productionDate());
        return form;
    }
}
