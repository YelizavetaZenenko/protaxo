package com.example.protaxo.contract.web;

import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.contract.dto.ContractFormData;
import com.example.protaxo.contract.dto.ContractRequest;
import com.example.protaxo.contract.dto.ContractResponse;
import com.example.protaxo.contract.entity.ContractStatus;
import com.example.protaxo.contract.service.ContractService;
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
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class ContractPageController {

    private final ContractService contractService;
    private final ClientService clientService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("contracts", contractService.findAll());
        model.addAttribute("clientNames", clientService.findAll().stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.name(), (a, b) -> a)));
        return "contracts/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("contract", new ContractFormData());
        addReferenceData(model);
        return "contracts/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("contract") ContractFormData form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            return "contracts/form";
        }
        contractService.create(toRequest(form));
        return "redirect:/contracts";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        ContractResponse response = contractService.findById(id);
        model.addAttribute("contract", toFormData(response));
        model.addAttribute("editId", id);
        addReferenceData(model);
        return "contracts/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("contract") ContractFormData form,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editId", id);
            addReferenceData(model);
            return "contracts/form";
        }
        contractService.update(id, toRequest(form));
        return "redirect:/contracts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        contractService.softDelete(id);
        return "redirect:/contracts";
    }

    private void addReferenceData(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("statuses", ContractStatus.values());
    }

    private ContractRequest toRequest(ContractFormData form) {
        return new ContractRequest(form.getClientId(), form.getContractNumber(), form.getStatus());
    }

    private ContractFormData toFormData(ContractResponse response) {
        ContractFormData form = new ContractFormData();
        form.setClientId(response.clientId());
        form.setContractNumber(response.contractNumber());
        form.setStatus(response.status());
        return form;
    }
}
