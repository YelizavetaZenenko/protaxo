package com.example.protaxo.invoice.web;

import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import com.example.protaxo.calibration.service.CalibrationProtocolService;
import com.example.protaxo.catalog.dto.CatalogItemResponse;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.service.CatalogItemService;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.util.UkrainianAmountWords;
import com.example.protaxo.contract.dto.ContractResponse;
import com.example.protaxo.contract.service.ContractService;
import com.example.protaxo.finance.dto.FinanceForms;
import com.example.protaxo.finance.dto.InvoiceSettlement;
import com.example.protaxo.finance.entity.PaymentMethod;
import com.example.protaxo.finance.service.FinanceQueryService;
import com.example.protaxo.finance.web.FinancePageController;
import com.example.protaxo.invoice.dto.ActSection;
import com.example.protaxo.invoice.dto.BillItemRow;
import com.example.protaxo.invoice.dto.InvoiceFormData;
import com.example.protaxo.invoice.dto.InvoiceItemFormData;
import com.example.protaxo.invoice.dto.InvoiceItemRequest;
import com.example.protaxo.invoice.dto.InvoiceItemResponse;
import com.example.protaxo.invoice.dto.InvoiceRequest;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.invoice.dto.VehiclePickerRow;
import com.example.protaxo.invoice.entity.InvoicePaymentType;
import com.example.protaxo.invoice.service.InvoiceService;
import com.example.protaxo.pdf.service.PdfRenderService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.thymeleaf.context.Context;

@Controller
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoicePageController {

    private static final String CALIBRATION_SERVICE_PREFIX = "Калібрування тахографа";

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final CatalogItemService catalogItemService;
    private final VehicleService vehicleService;
    private final CalibrationProtocolService calibrationProtocolService;
    private final ContractService contractService;
    private final PdfRenderService pdfRenderService;
    private final FinanceQueryService financeQueryService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "date") String sort,
                        @RequestParam(defaultValue = "desc") String dir,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filterDateFrom,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filterDateTo,
                        @RequestParam(required = false) InvoicePaymentType filterPaymentType,
                        @RequestParam(required = false) Long filterClientId,
                        Model model) {
        List<InvoiceResponse> invoices = filterInvoices(invoiceService.findAll(), filterDateFrom, filterDateTo,
                filterPaymentType, filterClientId);
        model.addAttribute("invoices", sortInvoices(invoices, sort, dir));
        model.addAttribute("settlements", financeQueryService.settlements(invoices));
        List<ClientResponse> clients = clientService.findAll();
        model.addAttribute("clients", clients);
        model.addAttribute("clientNames", clients.stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.name(), (a, b) -> a)));
        model.addAttribute("filterClientId", filterClientId);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("filterDateFrom", filterDateFrom);
        model.addAttribute("filterDateTo", filterDateTo);
        model.addAttribute("filterPaymentType", filterPaymentType);
        model.addAttribute("paymentTypes", InvoicePaymentType.values());
        return "invoices/list";
    }

    /**
     * Backs the "Автомобіль" modal picker on the form — see invoices/form.html.
     */
    @GetMapping(value = "/vehicle-picker", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<VehiclePickerRow> vehiclePicker(@RequestParam Long clientId) {
        Map<String, LocalDateTime> lastVisitByLabel = invoiceService.findLastVisitDatesByClientId(clientId);
        return vehicleService.findByClientId(clientId).stream()
                .map(v -> new VehiclePickerRow(v.id(), v.vin(), v.registrationNumber(), v.make(), v.model(),
                        v.year(), lastVisitByLabel.get(vehicleLabel(v))))
                .toList();
    }

    private String vehicleLabel(VehicleResponse v) {
        String makeModel = Stream.of(v.make(), v.model())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        return makeModel.isBlank() ? v.registrationNumber() : v.registrationNumber() + " (" + makeModel + ")";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        InvoiceResponse response = invoiceService.findById(id);
        model.addAttribute("invoice", response);
        model.addAttribute("client", clientService.findById(response.clientId()));

        boolean hasCalibrationService = response.items().stream()
                .anyMatch(i -> i.itemName() != null && i.itemName().startsWith(CALIBRATION_SERVICE_PREFIX));
        model.addAttribute("hasCalibrationService", hasCalibrationService);
        model.addAttribute("existingProtocolId", calibrationProtocolService.findByInvoiceId(id)
                .map(CalibrationProtocolResponse::id)
                .orElse(null));

        // Оплати за нарядом (docs/Фінансовий облік.md, розд. 6): стан розрахунків, історія
        // операцій і форма "Прийняти оплату". Форма після помилки повертається через flash.
        InvoiceSettlement settlement = financeQueryService.settlement(response);
        model.addAttribute("settlement", settlement);
        model.addAttribute("financeOperations", financeQueryService.operationsForInvoice(id));
        if (!model.containsAttribute("paymentForm")) {
            FinanceForms.Payment paymentForm = new FinanceForms.Payment();
            paymentForm.setInvoiceId(id);
            paymentForm.setAmount(settlement.debt());
            model.addAttribute("paymentForm", paymentForm);
        }
        // Новий ключ при кожному показі форми: подвійний клік на ту саму форму — не дубль,
        // а навмисна друга оплата після перезавантаження сторінки — окрема операція.
        ((FinanceForms.Payment) model.getAttribute("paymentForm")).setRequestKey(FinancePageController.newKey());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("paymentAccounts", financeQueryService.activeAccounts());
        return "invoices/view";
    }

    private List<InvoiceResponse> filterInvoices(List<InvoiceResponse> invoices, LocalDate filterDateFrom,
                                                  LocalDate filterDateTo, InvoicePaymentType filterPaymentType,
                                                  Long filterClientId) {
        return invoices.stream()
                .filter(inv -> filterDateFrom == null || !inv.documentDate().toLocalDate().isBefore(filterDateFrom))
                .filter(inv -> filterDateTo == null || !inv.documentDate().toLocalDate().isAfter(filterDateTo))
                .filter(inv -> filterPaymentType == null || inv.paymentType() == filterPaymentType)
                .filter(inv -> filterClientId == null || filterClientId.equals(inv.clientId()))
                .toList();
    }

    private List<InvoiceResponse> sortInvoices(List<InvoiceResponse> invoices, String sort, String dir) {
        Comparator<InvoiceResponse> comparator = "paymentType".equals(sort)
                ? Comparator.comparing(InvoiceResponse::paymentType)
                : Comparator.comparing(InvoiceResponse::documentDate);
        if ("desc".equals(dir)) {
            comparator = comparator.reversed();
        }
        return invoices.stream().sorted(comparator).toList();
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("invoice", new InvoiceFormData());
        model.addAttribute("materialRows", List.of());
        model.addAttribute("serviceRows", List.of());
        addReferenceData(model);
        return "invoices/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("invoice") InvoiceFormData form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("selectedClientName", resolveClientName(form.getClientId()));
            addItemRows(model, form);
            addReferenceData(model);
            return "invoices/form";
        }
        InvoiceResponse created;
        try {
            created = invoiceService.create(toRequest(form));
        } catch (BusinessRuleException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("selectedClientName", resolveClientName(form.getClientId()));
            addItemRows(model, form);
            addReferenceData(model);
            return "invoices/form";
        }
        boolean hasCalibrationService = created.items().stream()
                .anyMatch(i -> i.itemName() != null && i.itemName().startsWith(CALIBRATION_SERVICE_PREFIX));
        if (hasCalibrationService) {
            // Straight into the matching protocol — no need to detour through the invoice view
            // page just to click "Створити протокол" a second later.
            return "redirect:/calibration-protocols/new?invoiceId=" + created.id();
        }
        return "redirect:/invoices";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        InvoiceResponse response = invoiceService.findById(id);
        model.addAttribute("invoice", toFormData(response));
        model.addAttribute("editId", id);
        model.addAttribute("documentNumber", response.number());
        model.addAttribute("documentDate", response.documentDate());
        model.addAttribute("buyerOrderLabel", response.buyerOrderLabel());
        model.addAttribute("totalAmount", response.totalAmount());
        model.addAttribute("selectedClientName", clientService.findById(response.clientId()).name());
        addItemRows(model, response);
        addReferenceData(model);
        return "invoices/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("invoice") InvoiceFormData form,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editId", id);
            model.addAttribute("selectedClientName", resolveClientName(form.getClientId()));
            addItemRows(model, form);
            addReferenceData(model);
            return "invoices/form";
        }
        try {
            invoiceService.update(id, toRequest(form));
        } catch (BusinessRuleException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("editId", id);
            model.addAttribute("selectedClientName", resolveClientName(form.getClientId()));
            addItemRows(model, form);
            addReferenceData(model);
            return "invoices/form";
        }
        return "redirect:/invoices";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            invoiceService.softDelete(id);
        } catch (BusinessRuleException ex) {
            redirect.addFlashAttribute("listError", ex.getMessage());
        }
        return "redirect:/invoices";
    }

    /**
     * "Рахунок на оплату по замовленню" — laid out after the user's sample bill (photo of a paper
     * bill, 2026-10-19): warning notice, "зразок заповнення платіжного доручення" box,
     * supplier/buyer/contract (number + date) block, a compact items table with prices without
     * VAT, Разом / Сума ПДВ / Усього з ПДВ, total and VAT in words, "Виписав(ла)".
     */
    @GetMapping("/{id}/bill-pdf")
    public void printBillPdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        InvoiceResponse invoice = invoiceService.findById(id);

        List<BillItemRow> rows = invoice.items().stream().map(this::toBillItemRow).toList();

        Context context = documentContext(invoice);
        context.setVariable("rows", rows);
        context.setVariable("hasDiscount", rows.stream().anyMatch(r -> !r.discountDisplay().isEmpty()));
        byte[] pdf = pdfRenderService.render("invoice-bill", context);

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition", "inline; filename=\"bill-" + id + ".pdf\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }

    /**
     * "Акт виконаних робіт з технічного обслуговування і ремонту транспортного засобу" — laid out
     * after the user's sample act (same photo set): vehicle/owner table, separate "Надані
     * матеріали" and "Надані послуги" blocks each with Всього / Знижка / ПДВ / Всього з ПДВ,
     * До оплати (in words), warranty terms, hand-over/acceptance signatures and both parties'
     * requisites. See docs/"Наряд-заказ".md.
     */
    @GetMapping("/{id}/act-pdf")
    public void printActPdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        InvoiceResponse invoice = invoiceService.findById(id);

        List<BillItemRow> rows = invoice.items().stream().map(this::toBillItemRow).toList();

        Context context = documentContext(invoice);
        context.setVariable("materials", ActSection.of(rows.stream().filter(r -> !r.service()).toList()));
        context.setVariable("services", ActSection.of(rows.stream().filter(BillItemRow::service).toList()));
        context.setVariable("vehicle", findInvoiceVehicle(invoice));
        byte[] pdf = pdfRenderService.render("act", context);
        invoiceService.markActPrinted(id);

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition", "inline; filename=\"act-" + id + ".pdf\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }

    /** Variables shared by the bill and the act: parties, contract, totals and their words. */
    private Context documentContext(InvoiceResponse invoice) {
        ContractResponse contract = contractService.findLatestByClientId(invoice.clientId()).orElse(null);

        Context context = new Context(new Locale("uk"));
        context.setVariable("invoice", invoice);
        context.setVariable("client", clientService.findById(invoice.clientId()));
        context.setVariable("totalAmount", invoice.totalAmount());
        context.setVariable("totalVat", invoice.totalVat());
        context.setVariable("totalWithoutVat", invoice.totalWithoutVat());
        context.setVariable("amountInWords", UkrainianAmountWords.amountToWords(invoice.totalAmount()));
        context.setVariable("vatInWords", UkrainianAmountWords.amountToWords(invoice.totalVat()));
        context.setVariable("contractNumber", contract == null ? null : contract.contractNumber());
        context.setVariable("contractDate", contract == null ? null : contract.contractDate());
        return context;
    }

    /**
     * The наряд stores the vehicle only as its picker label ("AC1466HI (Volvo FH)"), so the act's
     * VIN / make / plate come from matching that label against the client's vehicles; null if the
     * vehicle was since edited or removed — the act then prints just the stored label.
     */
    private VehicleResponse findInvoiceVehicle(InvoiceResponse invoice) {
        if (invoice.vehicleName() == null || invoice.vehicleName().isBlank()) {
            return null;
        }
        return vehicleService.findByClientId(invoice.clientId()).stream()
                .filter(v -> vehicleLabel(v).equals(invoice.vehicleName()))
                .findFirst()
                .orElse(null);
    }

    private BillItemRow toBillItemRow(InvoiceItemResponse item) {
        boolean service = item.catalogItemType() == CatalogItemType.SERVICE;
        String unit = service ? "послуга" : item.unitLabel();
        // ПДВ рахується й зберігається під час збереження наряду (InvoiceService.applyItems) —
        // PDF лише показує збережені суми, щоб документ збігався з обліком. Ціна в наряді — з ПДВ,
        // документи ж показують ціну без ПДВ; без знижки сума до знижки = збережена сума без ПДВ.
        BigDecimal priceWithoutVat = item.price().subtract(item.vatRate().vatFrom(item.price()));
        boolean discounted = item.discountPercent() != null && item.discountPercent().signum() != 0;
        BigDecimal grossWithoutVat = discounted
                ? item.quantity().multiply(priceWithoutVat).setScale(2, RoundingMode.HALF_UP)
                : item.amountWithoutVat();
        return new BillItemRow(item.lineNumber(), item.itemName(), unit, item.quantity(), item.price(),
                item.discountPercent(), item.amountWithoutVat(), item.vatRate().getLabel(), item.vatAmount(), item.amount(),
                service, priceWithoutVat, grossWithoutVat);
    }

    /** Строк оплати, який отримає наряд після збереження: дата наряду (для нового — сьогодні) + 15 днів. */
    private LocalDate paymentDueDate(Long editId) {
        LocalDate documentDay = editId == null ? LocalDate.now() : invoiceService.findById(editId).documentDate().toLocalDate();
        return documentDay.plusDays(InvoiceService.PAYMENT_TERM_DAYS);
    }

    private String resolveClientName(Long clientId) {
        return clientId == null ? null : clientService.findById(clientId).name();
    }

    private void addReferenceData(Model model) {
        model.addAttribute("paymentDueDate", paymentDueDate((Long) model.getAttribute("editId")));
        List<CatalogItemResponse> catalogItems = catalogItemService.findAll();
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("paymentTypes", InvoicePaymentType.values());
        model.addAttribute("materialCatalogItems", catalogItems.stream()
                .filter(ci -> ci.type() == CatalogItemType.MATERIAL)
                .toList());
        model.addAttribute("serviceCatalogItems", catalogItems.stream()
                .filter(ci -> ci.type() == CatalogItemType.SERVICE)
                .toList());
    }

    /**
     * "Товари" and "Послуги" are two visual tables backed by the same underlying {@code items}
     * list, so each row must keep binding to its original index in that combined list.
     */
    private void addItemRows(Model model, InvoiceResponse response) {
        List<InvoiceItemResponse> items = response.items();
        List<Integer> materialRows = new ArrayList<>();
        List<Integer> serviceRows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            (items.get(i).catalogItemType() == CatalogItemType.SERVICE ? serviceRows : materialRows).add(i);
        }
        model.addAttribute("materialRows", materialRows);
        model.addAttribute("serviceRows", serviceRows);
    }

    private void addItemRows(Model model, InvoiceFormData form) {
        List<InvoiceItemFormData> items = form.getItems();
        Map<Long, CatalogItemType> typeById = catalogItemService.findAll().stream()
                .collect(Collectors.toMap(CatalogItemResponse::id, CatalogItemResponse::type));
        List<Integer> materialRows = new ArrayList<>();
        List<Integer> serviceRows = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                CatalogItemType type = typeById.get(items.get(i).getCatalogItemId());
                (type == CatalogItemType.SERVICE ? serviceRows : materialRows).add(i);
            }
        }
        model.addAttribute("materialRows", materialRows);
        model.addAttribute("serviceRows", serviceRows);
    }

    private InvoiceRequest toRequest(InvoiceFormData form) {
        List<InvoiceItemRequest> items = form.getItems() == null ? List.of() : form.getItems().stream()
                .map(i -> new InvoiceItemRequest(i.getCatalogItemId(), i.getQuantity(), i.getPrice(), i.getDiscountPercent()))
                .toList();
        return new InvoiceRequest(form.getPaymentType(), form.getClientId(),
                form.getVehicleName(), form.getDriverName(), form.getRepairResponsibleName(),
                form.getRepairSupervisorName(), items);
    }

    private InvoiceFormData toFormData(InvoiceResponse response) {
        InvoiceFormData form = new InvoiceFormData();
        form.setPaymentType(response.paymentType());
        form.setClientId(response.clientId());
        form.setVehicleName(response.vehicleName());
        form.setDriverName(response.driverName());
        form.setRepairResponsibleName(response.repairResponsibleName());
        form.setRepairSupervisorName(response.repairSupervisorName());
        form.setItems(response.items().stream()
                .map(i -> {
                    InvoiceItemFormData itemForm = new InvoiceItemFormData();
                    itemForm.setCatalogItemId(i.catalogItemId());
                    itemForm.setQuantity(i.quantity());
                    itemForm.setPrice(i.price());
                    itemForm.setDiscountPercent(i.discountPercent() == null || i.discountPercent().signum() == 0
                            ? null : i.discountPercent().stripTrailingZeros());
                    return itemForm;
                })
                .collect(Collectors.toCollection(ArrayList::new)));
        return form;
    }
}
