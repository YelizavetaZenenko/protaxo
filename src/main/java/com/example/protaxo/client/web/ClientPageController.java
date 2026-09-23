package com.example.protaxo.client.web;

import com.example.protaxo.client.dto.ClientFormData;
import com.example.protaxo.client.dto.ClientRequest;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.driver.dto.DriverResponse;
import com.example.protaxo.driver.service.DriverService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientPageController {

    private final ClientService clientService;
    private final DriverService driverService;
    private final VehicleService vehicleService;

    @GetMapping
    public String list(@RequestParam(required = false) String filterName,
                        @RequestParam(required = false) String filterEdrpou,
                        Model model) {
        model.addAttribute("clients", filterClients(clientService.findAll(), filterName, filterEdrpou));
        model.addAttribute("filterName", filterName);
        model.addAttribute("filterEdrpou", filterEdrpou);
        return "clients/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("client", clientService.findById(id));
        model.addAttribute("drivers", driverService.findByClientId(id));
        model.addAttribute("vehicles", vehicleService.findByClientId(id));
        return "clients/view";
    }

    private List<ClientResponse> filterClients(List<ClientResponse> clients, String filterName, String filterEdrpou) {
        return clients.stream()
                .filter(c -> isBlank(filterName) || containsIgnoreCase(c.name(), filterName))
                .filter(c -> isBlank(filterEdrpou) || containsIgnoreCase(c.edrpou(), filterEdrpou))
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase().contains(search.toLowerCase());
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("client", new ClientFormData());
        model.addAttribute("drivers", List.<DriverResponse>of());
        model.addAttribute("vehicles", List.<VehicleResponse>of());
        return "clients/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("client") ClientFormData form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("drivers", List.<DriverResponse>of());
            model.addAttribute("vehicles", List.<VehicleResponse>of());
            return "clients/form";
        }
        try {
            clientService.create(toRequest(form, null));
        } catch (BusinessRuleException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("drivers", List.<DriverResponse>of());
            model.addAttribute("vehicles", List.<VehicleResponse>of());
            return "clients/form";
        }
        return "redirect:/clients";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        ClientResponse response = clientService.findById(id);
        model.addAttribute("client", toFormData(response));
        model.addAttribute("editId", id);
        addChildData(model, id);
        return "clients/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("client") ClientFormData form,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editId", id);
            addChildData(model, id);
            return "clients/form";
        }
        ClientResponse existing = clientService.findById(id);
        try {
            clientService.update(id, toRequest(form, existing));
        } catch (BusinessRuleException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("editId", id);
            addChildData(model, id);
            return "clients/form";
        }
        return "redirect:/clients";
    }

    /** Drivers/vehicles for the "Водії"/"Автомобілі" sections shown only in edit mode. */
    private void addChildData(Model model, Long id) {
        model.addAttribute("drivers", driverService.findByClientId(id));
        model.addAttribute("vehicles", vehicleService.findByClientId(id));
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        clientService.softDelete(id);
        return "redirect:/clients";
    }

    /**
     * The form no longer collects contactPersonName/birthDate/gender/employerClientId/position/
     * phone (their inputs were removed in earlier iterations — see [[Контрагенти]]), so
     * {@code form} never carries them. On update, those fields must come from {@code existing} or
     * a plain "Зберегти" click would silently null them out for every client that had them set
     * some other way (e.g. via the "Працівники" picker, which still sets employerClientId/
     * position through the REST API). {@code existing} is null on create, where there's nothing
     * to preserve.
     */
    private ClientRequest toRequest(ClientFormData form, ClientResponse existing) {
        return new ClientRequest(form.getName(), form.getEdrpou(), form.getFullName(),
                existing != null ? existing.contactPersonName() : null,
                form.getContactPersonPhone(), form.getCode(), form.getLastName(), form.getFirstName(),
                form.getMiddleName(),
                existing != null ? existing.birthDate() : null,
                existing != null ? existing.gender() : null,
                existing != null ? existing.employerClientId() : null,
                existing != null ? existing.position() : null,
                existing != null ? existing.phone() : null,
                form.getEmail());
    }

    private ClientFormData toFormData(ClientResponse response) {
        ClientFormData form = new ClientFormData();
        form.setName(response.name());
        form.setEdrpou(response.edrpou());
        form.setFullName(response.fullName());
        form.setContactPersonPhone(response.contactPersonPhone());
        form.setCode(response.code());
        form.setLastName(response.lastName());
        form.setFirstName(response.firstName());
        form.setMiddleName(response.middleName());
        form.setEmail(response.email());
        return form;
    }
}
