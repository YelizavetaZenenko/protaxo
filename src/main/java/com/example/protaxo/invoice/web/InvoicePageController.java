package com.example.protaxo.invoice.web;

import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import com.example.protaxo.calibration.service.CalibrationProtocolService;
import com.example.protaxo.catalog.dto.CatalogItemResponse;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.service.CatalogItemService;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.common.util.UkrainianAmountWords;
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
import org.thymeleaf.context.Context;

@Controller
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoicePageController {

    private static final String CALIBRATION_SERVICE_PREFIX = "Калібрування тахографа";
    private static final BigDecimal VAT_RATE_NUMERATOR = BigDecimal.valueOf(20);
    private static final BigDecimal VAT_RATE_DIVISOR = BigDecimal.valueOf(120);

    private final InvoiceService invoiceService;
    private final ClientService clientService;
    private final CatalogItemService catalogItemService;
    private final VehicleService vehicleService;
    private final CalibrationProtocolService calibrationProtocolService;
    private final PdfRenderService pdfRenderService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "date") String sort,
                        @RequestParam(defaultValue = "desc") String dir,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filterDate,
                        @RequestParam(required = false) InvoicePaymentType filterPaymentType,
                        Model model) {
        List<InvoiceResponse> invoices = filterInvoices(invoiceService.findAll(), filterDate, filterPaymentType);
        model.addAttribute("invoices", sortInvoices(invoices, sort, dir));
        model.addAttribute("clientNames", clientService.findAll().stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.name(), (a, b) -> a)));
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("filterDate", filterDate);
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
        return "invoices/view";
    }

    private List<InvoiceResponse> filterInvoices(List<InvoiceResponse> invoices, LocalDate filterDate,
                                                  InvoicePaymentType filterPaymentType) {
        return invoices.stream()
                .filter(inv -> filterDate == null || inv.documentDate().toLocalDate().equals(filterDate))
                .filter(inv -> filterPaymentType == null || inv.paymentType() == filterPaymentType)
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
        invoiceService.create(toRequest(form));
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
        invoiceService.update(id, toRequest(form));
        return "redirect:/invoices";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        invoiceService.softDelete(id);
        return "redirect:/invoices";
    }

    @GetMapping("/{id}/pdf")
    public void printPdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        InvoiceResponse invoice = invoiceService.findById(id);
        String clientName = clientService.findById(invoice.clientId()).name();

        Context context = new Context();
        context.setVariable("invoice", invoice);
        context.setVariable("clientName", clientName);
        byte[] pdf = pdfRenderService.render("invoice", context);

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition", "inline; filename=\"invoice-" + id + ".pdf\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }

    /**
     * "Рахунок на оплату" — a distinct document from the plain Наряд-заказ PDF above, modeled
     * on the sample the user provided (rahunok-na-oplatu-2026-roku-vid-01.07.2025.docx): warning
     * notice, a "зразок заповнення платіжного доручення" mini-table (with blank space reserved
     * for a QR code the user stamps on manually), supplier/buyer/contract block, an items table
     * with a 20% VAT breakdown, total in words, and a validity date. Постачальник requisites and
     * the Договір number are placeholder/blank text — the system has no data about its own
     * company yet, and the contract number is filled in by hand.
     */
    @GetMapping("/{id}/bill-pdf")
    public void printBillPdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        InvoiceResponse invoice = invoiceService.findById(id);

        List<BillItemRow> rows = invoice.items().stream().map(this::toBillItemRow).toList();
        BigDecimal totalAmount = invoice.totalAmount();
        BigDecimal totalVat = rows.stream().map(BillItemRow::vatAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWithoutVat = totalAmount.subtract(totalVat);
        String amountInWords = UkrainianAmountWords.amountToWords(totalAmount);

        Context context = new Context(new Locale("uk"));
        context.setVariable("invoice", invoice);
        context.setVariable("client", clientService.findById(invoice.clientId()));
        context.setVariable("rows", rows);
        context.setVariable("totalAmount", totalAmount);
        context.setVariable("totalVat", totalVat);
        context.setVariable("totalWithoutVat", totalWithoutVat);
        context.setVariable("amountInWords", amountInWords);
        byte[] pdf = pdfRenderService.render("invoice-bill", context);

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition", "inline; filename=\"bill-" + id + ".pdf\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }

    private BillItemRow toBillItemRow(InvoiceItemResponse item) {
        String unit = item.catalogItemType() == CatalogItemType.SERVICE ? "послуга" : "шт.";
        BigDecimal amount = item.amount();
        BigDecimal vatAmount = amount.multiply(VAT_RATE_NUMERATOR)
                .divide(VAT_RATE_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal amountWithoutVat = amount.subtract(vatAmount);
        return new BillItemRow(item.lineNumber(), item.itemName(), unit, item.quantity(), item.price(),
                amountWithoutVat, vatAmount, amount);
    }

    private String resolveClientName(Long clientId) {
        return clientId == null ? null : clientService.findById(clientId).name();
    }

    private void addReferenceData(Model model) {
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
                .map(i -> new InvoiceItemRequest(i.getCatalogItemId(), i.getQuantity(), i.getPrice()))
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
                    return itemForm;
                })
                .collect(Collectors.toCollection(ArrayList::new)));
        return form;
    }
}
