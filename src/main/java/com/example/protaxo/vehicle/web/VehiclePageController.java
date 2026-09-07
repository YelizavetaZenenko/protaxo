package com.example.protaxo.vehicle.web;

import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.vehicle.dto.VehicleFormData;
import com.example.protaxo.vehicle.dto.VehicleRequest;
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
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/vehicles")
@RequiredArgsConstructor
public class VehiclePageController {

    private final VehicleService vehicleService;
    private final ClientService clientService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("vehicles", vehicleService.findAll());
        model.addAttribute("clientNames", clientService.findAll().stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.name(), (a, b) -> a)));
        return "vehicles/list";
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) Long clientId, Model model) {
        VehicleFormData form = new VehicleFormData();
        if (clientId != null) {
            form.setClientId(clientId);
            form.setReturnToClientId(clientId);
        }
        model.addAttribute("vehicle", form);
        addReferenceData(model);
        return "vehicles/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("vehicle") VehicleFormData form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            return "vehicles/form";
        }
        vehicleService.create(toRequest(form));
        if (form.getReturnToClientId() != null) {
            return "redirect:/clients/" + form.getReturnToClientId() + "/edit";
        }
        return "redirect:/vehicles";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        VehicleResponse response = vehicleService.findById(id);
        model.addAttribute("vehicle", toFormData(response));
        model.addAttribute("editId", id);
        addReferenceData(model);
        return "vehicles/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("vehicle") VehicleFormData form,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editId", id);
            addReferenceData(model);
            return "vehicles/form";
        }
        vehicleService.update(id, toRequest(form));
        return "redirect:/vehicles";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        vehicleService.softDelete(id);
        return "redirect:/vehicles";
    }

    private void addReferenceData(Model model) {
        model.addAttribute("clients", clientService.findAll());
    }

    private VehicleRequest toRequest(VehicleFormData form) {
        return new VehicleRequest(form.getClientId(), form.getVin(), form.getRegistrationNumber(),
                form.getMake(), form.getModel(), form.getYear());
    }

    private VehicleFormData toFormData(VehicleResponse response) {
        VehicleFormData form = new VehicleFormData();
        form.setClientId(response.clientId());
        form.setVin(response.vin());
        form.setRegistrationNumber(response.registrationNumber());
        form.setMake(response.make());
        form.setModel(response.model());
        form.setYear(response.year());
        return form;
    }
}
