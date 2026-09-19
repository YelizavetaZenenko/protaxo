package com.example.protaxo.contract.web;

import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.contract.dto.ContractFormData;
import com.example.protaxo.contract.dto.ContractRequest;
import com.example.protaxo.contract.dto.ContractResponse;
import com.example.protaxo.contract.service.ContractService;
import com.example.protaxo.pdf.service.PdfRenderService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.thymeleaf.context.Context;

@Controller
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class ContractPageController {

    private final ContractService contractService;
    private final ClientService clientService;
    private final PdfRenderService pdfRenderService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("contracts", contractService.findAll());
        model.addAttribute("clientNames", clientService.findAll().stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.name(), (a, b) -> a)));
        return "contracts/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        ContractResponse contract = contractService.findById(id);
        model.addAttribute("contract", contract);
        model.addAttribute("clientName", clientService.findById(contract.clientId()).name());
        return "contracts/view";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("contract", new ContractFormData());
        model.addAttribute("contractNumber", null);
        addReferenceData(model);
        return "contracts/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("contract") ContractFormData form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("contractNumber", null);
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
        model.addAttribute("contractNumber", response.contractNumber());
        model.addAttribute("editId", id);
        addReferenceData(model);
        return "contracts/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("contract") ContractFormData form,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editId", id);
            model.addAttribute("contractNumber", contractService.findById(id).contractNumber());
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

    @GetMapping("/{id}/pdf")
    public void printPdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        ContractResponse contract = contractService.findById(id);
        ClientResponse client = clientService.findById(contract.clientId());

        Context context = new Context(new Locale("uk"));
        context.setVariable("contract", contract);
        context.setVariable("client", client);
        byte[] pdf = pdfRenderService.render("contract", context);

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition", "inline; filename=\"contract-" + id + ".pdf\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }

    private void addReferenceData(Model model) {
        model.addAttribute("clients", clientService.findAll());
    }

    private ContractRequest toRequest(ContractFormData form) {
        return new ContractRequest(form.getClientId());
    }

    private ContractFormData toFormData(ContractResponse response) {
        ContractFormData form = new ContractFormData();
        form.setClientId(response.clientId());
        return form;
    }
}
