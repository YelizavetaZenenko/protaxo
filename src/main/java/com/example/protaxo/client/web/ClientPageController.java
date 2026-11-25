package com.example.protaxo.client.web;

import com.example.protaxo.address.AddressDirectoryService;
import com.example.protaxo.bank.Bank;
import com.example.protaxo.bank.BankService;
import com.example.protaxo.client.dto.ClientFormData;
import com.example.protaxo.client.dto.ClientRequest;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.entity.LegalForm;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.driver.dto.DriverResponse;
import com.example.protaxo.driver.service.DriverService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final BankService bankService;
    private final AddressDirectoryService addressDirectoryService;

    /** Області для поля «Юридична адреса» (КАТОТТГ, V73). */
    @ModelAttribute("addressRegions")
    public List<AddressDirectoryService.Region> addressRegions() {
        return addressDirectoryService.regions();
    }

    /** Форми власності для списку ліворуч від назви. */
    @ModelAttribute("legalForms")
    public LegalForm[] legalForms() {
        return LegalForm.values();
    }

    /** Довідник банків для випадного списку «Банк» у формі (docs/Контрагенти.md). */
    @ModelAttribute("banks")
    public List<Bank> banks() {
        return bankService.findAll();
    }

    /**
     * Пошук одним рядком (фільтрує сторінка під час введення): кожне слово запиту має знайтися в
     * тексті контрагента — назва, повна назва, ЄДРПОУ, ІПН, ПІБ, телефони (і самі цифри), email,
     * адреса, рахунки, банк, МФО. {@code q} з адреси лише підставляється в поле пошуку.
     */
    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        List<ClientResponse> clients = clientService.findAll();
        Map<Long, String> extraIbans = clientService.extraIbansByClient();
        Map<Long, String> searchText = new HashMap<>();
        for (ClientResponse c : clients) {
            searchText.put(c.id(), searchText(c, extraIbans.get(c.id())));
        }
        model.addAttribute("clients", clients);
        model.addAttribute("searchText", searchText);
        model.addAttribute("q", q);
        return "clients/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("client", clientService.findById(id));
        model.addAttribute("extraAccounts", clientService.findExtraAccounts(id));
        model.addAttribute("drivers", driverService.findByClientId(id));
        model.addAttribute("vehicles", vehicleService.findByClientId(id));
        return "clients/view";
    }

    private static String searchText(ClientResponse c, String extraIbans) {
        return normalize(Stream.of(c.name(), c.fullName(), c.edrpou(), c.code(), c.lastName(), c.firstName(),
                        c.middleName(), c.contactPersonName(), c.contactPersonPhone(), c.phone(), c.email(),
                        c.legalAddress(), c.iban(), c.bankName(), c.mfo(), extraIbans,
                        digits(c.contactPersonPhone()), digits(c.phone()))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" ")));
    }

    private static String digits(String phone) {
        return phone == null ? null : phone.replaceAll("\\D", "");
    }

    /** Нижній регістр, без лапок, одинарні пробіли — щоб «"Агро"» і «агро» збігались. */
    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[«»\"'’ʼ]", "").replaceAll("\\s+", " ").trim();
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
        checkCodes(form, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("drivers", List.<DriverResponse>of());
            model.addAttribute("vehicles", List.<VehicleResponse>of());
            return "clients/form";
        }
        try {
            clientService.create(toRequest(form, null), form.getExtraAccounts());
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
        ClientFormData form = toFormData(response);
        form.setExtraAccounts(new ArrayList<>(clientService.findExtraAccounts(id)));
        model.addAttribute("client", form);
        model.addAttribute("editId", id);
        addChildData(model, id);
        return "clients/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("client") ClientFormData form,
                          BindingResult bindingResult, Model model) {
        checkCodes(form, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("editId", id);
            addChildData(model, id);
            return "clients/form";
        }
        ClientResponse existing = clientService.findById(id);
        try {
            clientService.update(id, toRequest(form, existing), form.getExtraAccounts());
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
    /**
     * Довжина кодів залежить від форми власності: ФОП — ІПН (РНОКПП) 10 цифр, і код ЄДРПОУ той самий
     * (форма підставляє його сама, тут — на випадок, якщо JS не спрацював); решта — ЄДРПОУ 8, ІПН 12. Обидва коди необов'язкові.
     */
    private static void checkCodes(ClientFormData form, BindingResult bindingResult) {
        String code = form.getCode() == null ? "" : form.getCode().trim();
        if (form.getLegalForm() == LegalForm.FOP) {
            if (!code.isEmpty() && !code.matches("\\d{10}")) {
                bindingResult.rejectValue("code", "fop", "У ФОП код ІПН (РНОКПП) — рівно 10 цифр");
            }
            form.setEdrpou(code);
            return;
        }
        String edrpou = form.getEdrpou() == null ? "" : form.getEdrpou().trim();
        if (!edrpou.isEmpty() && !edrpou.matches("\\d{8}")) {
            bindingResult.rejectValue("edrpou", "edrpou", "Код ЄДРПОУ має складатись рівно з 8 цифр");
        }
        if (!code.isEmpty() && !code.matches("\\d{12}")) {
            bindingResult.rejectValue("code", "code", "Код ІПН має складатись рівно з 12 цифр");
        }
    }

    /** Коротка й повна назва — з форми власності й назви; повна, введена вручну, лишається як є. */
    private ClientRequest toRequest(ClientFormData form, ClientResponse existing) {
        LegalForm legalForm = form.getLegalForm() == null ? LegalForm.OTHER : form.getLegalForm();
        String name = legalForm.shortName(form.getName());
        String fullName = blankToNull(form.getFullName()) != null ? form.getFullName().trim() : legalForm.fullName(form.getName());
        return new ClientRequest(name, blankToNull(form.getEdrpou()), fullName,
                existing != null ? existing.contactPersonName() : null,
                form.getContactPersonPhone(), blankToNull(form.getCode()), form.getLastName(), form.getFirstName(),
                blankToNull(form.getMiddleName()),
                existing != null ? existing.birthDate() : null,
                existing != null ? existing.gender() : null,
                existing != null ? existing.employerClientId() : null,
                existing != null ? existing.position() : null,
                existing != null ? existing.phone() : null,
                blankToNull(form.getEmail()), form.isVatPayer(),
                blankToNull(form.getLegalAddress()), blankToNull(form.getIban()), blankToNull(form.getBankName()),
                blankToNull(form.getMfo()), blankToNull(form.getSignatoryPosition()), blankToNull(form.getSignatoryBasis()),
                blankToNull(form.getAddressSettlementCode()), blankToNull(form.getAddressStreet()),
                blankToNull(form.getAddressBuilding()), blankToNull(form.getAddressOffice()),
                blankToNull(form.getAddressPostalCode()), form.getLegalForm());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ClientFormData toFormData(ClientResponse response) {
        ClientFormData form = new ClientFormData();
        form.setLegalForm(response.legalForm());
        form.setName(response.legalForm() == null ? response.name() : response.legalForm().stripFrom(response.name()));
        form.setEdrpou(response.edrpou());
        form.setFullName(response.fullName());
        form.setContactPersonPhone(response.contactPersonPhone());
        form.setCode(response.code());
        form.setLastName(response.lastName());
        form.setFirstName(response.firstName());
        form.setMiddleName(response.middleName());
        form.setEmail(response.email());
        form.setVatPayer(response.vatPayer());
        form.setLegalAddress(response.legalAddress());
        form.setIban(response.iban());
        form.setBankName(response.bankName());
        form.setMfo(response.mfo());
        form.setSignatoryPosition(response.signatoryPosition());
        form.setSignatoryBasis(response.signatoryBasis());
        form.setAddressSettlementCode(response.addressSettlementCode());
        form.setAddressStreet(response.addressStreet());
        form.setAddressBuilding(response.addressBuilding());
        form.setAddressOffice(response.addressOffice());
        form.setAddressPostalCode(response.addressPostalCode());
        return form;
    }
}
