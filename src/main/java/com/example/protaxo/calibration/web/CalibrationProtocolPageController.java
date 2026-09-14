package com.example.protaxo.calibration.web;

import com.example.protaxo.calibration.dto.CalibrationProtocolFormData;
import com.example.protaxo.calibration.dto.CalibrationProtocolRequest;
import com.example.protaxo.calibration.dto.CalibrationProtocolResponse;
import com.example.protaxo.calibration.service.CalibrationProtocolService;
import com.example.protaxo.client.service.ClientService;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.invoice.service.InvoiceService;
import com.example.protaxo.pdf.service.PdfRenderService;
import com.example.protaxo.suggestion.entity.FieldSuggestionCategory;
import com.example.protaxo.suggestion.service.FieldSuggestionService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import com.example.protaxo.vehicle.service.VehicleService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
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
import org.thymeleaf.context.Context;

@Controller
@RequestMapping("/calibration-protocols")
@RequiredArgsConstructor
public class CalibrationProtocolPageController {

    private final CalibrationProtocolService calibrationProtocolService;
    private final ClientService clientService;
    private final InvoiceService invoiceService;
    private final VehicleService vehicleService;
    private final FieldSuggestionService fieldSuggestionService;
    private final PdfRenderService pdfRenderService;

    @GetMapping
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filterDate,
                        @RequestParam(required = false) String filterInternalNumber,
                        Model model) {
        model.addAttribute("protocols", filterProtocols(calibrationProtocolService.findAll(), filterDate, filterInternalNumber));
        model.addAttribute("filterDate", filterDate);
        model.addAttribute("filterInternalNumber", filterInternalNumber);
        model.addAttribute("clientNames", clientService.findAll().stream()
                .collect(Collectors.toMap(c -> c.id(), c -> c.name(), (a, b) -> a)));
        return "calibration-protocols/list";
    }

    private List<CalibrationProtocolResponse> filterProtocols(List<CalibrationProtocolResponse> protocols,
                                                                LocalDate filterDate, String filterInternalNumber) {
        return protocols.stream()
                .filter(p -> filterDate == null || p.protocolDate().toLocalDate().equals(filterDate))
                .filter(p -> filterInternalNumber == null || filterInternalNumber.isBlank()
                        || p.internalNumber().toLowerCase().contains(filterInternalNumber.toLowerCase()))
                .toList();
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(required = false) Long invoiceId, Model model) {
        CalibrationProtocolFormData form = new CalibrationProtocolFormData();
        if (invoiceId != null) {
            InvoiceResponse invoice = invoiceService.findById(invoiceId);
            form.setInvoiceId(invoiceId);
            form.setClientId(invoice.clientId());
            form.setVehicleName(invoice.vehicleName());
            // Internal number is tied to the invoice's own number — one unified system,
            // never independently typed when a protocol is created from a наряд-заказ.
            form.setInternalNumber(invoice.number());
            // "Представник" — the carrier's person present with the vehicle is the driver on
            // the наряд-заказ, the closest match to "уповноважена особа автомобільного
            // перевізника" on the official protocol form.
            form.setRepresentativeName(invoice.driverName());
            applyVehicleDetails(form, invoice.clientId(), invoice.vehicleName());
        }
        model.addAttribute("protocol", form);
        addReferenceData(model);
        return "calibration-protocols/form";
    }

    /**
     * {@code Invoice.vehicleName} is free text (no FK — see [[Наряд-заказ]]), so the only way to
     * recover the real VRN/VIN when auto-filling a protocol from a наряд-заказ is the same
     * best-effort label match already used for "дата останнього візиту" on the invoice form
     * ({@code InvoicePageController.vehicleLabel}) — duplicated here rather than shared, per the
     * project's convention of small per-controller helpers over cross-module utilities.
     */
    private void applyVehicleDetails(CalibrationProtocolFormData form, Long clientId, String vehicleName) {
        if (clientId == null || vehicleName == null) {
            return;
        }
        vehicleService.findByClientId(clientId).stream()
                .filter(v -> vehicleLabel(v).equals(vehicleName))
                .findFirst()
                .ifPresent(v -> {
                    form.setVehicleVrn(v.registrationNumber());
                    form.setVehicleVin(v.vin());
                });
    }

    private String vehicleLabel(VehicleResponse v) {
        String makeModel = Stream.of(v.make(), v.model())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        return makeModel.isBlank() ? v.registrationNumber() : v.registrationNumber() + " (" + makeModel + ")";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("protocol") CalibrationProtocolFormData form, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            return "calibration-protocols/form";
        }
        CalibrationProtocolResponse created = calibrationProtocolService.create(toRequest(form));
        return "redirect:/calibration-protocols/" + created.id();
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        CalibrationProtocolResponse response = calibrationProtocolService.findById(id);
        model.addAttribute("protocol", response);
        model.addAttribute("client", clientService.findById(response.clientId()));
        return "calibration-protocols/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        CalibrationProtocolResponse response = calibrationProtocolService.findById(id);
        model.addAttribute("protocol", toFormData(response));
        model.addAttribute("editId", id);
        model.addAttribute("protocolNumber", response.protocolNumber());
        model.addAttribute("protocolDate", response.protocolDate());
        model.addAttribute("orderLabel", response.orderLabel());
        addReferenceData(model);
        return "calibration-protocols/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("protocol") CalibrationProtocolFormData form,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editId", id);
            addReferenceData(model);
            return "calibration-protocols/form";
        }
        calibrationProtocolService.update(id, toRequest(form));
        return "redirect:/calibration-protocols/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        calibrationProtocolService.softDelete(id);
        return "redirect:/calibration-protocols";
    }

    @GetMapping("/{id}/pdf")
    public void printPdf(@PathVariable Long id, HttpServletResponse response) throws IOException {
        CalibrationProtocolResponse protocol = calibrationProtocolService.findById(id);
        String clientName = clientService.findById(protocol.clientId()).name();

        Context context = new Context();
        context.setVariable("protocol", protocol);
        context.setVariable("clientName", clientName);
        context.setVariable("logoDataUri", pdfRenderService.classpathImageDataUri("images/logo.png", "image/png"));
        byte[] pdf = pdfRenderService.render("calibration-protocol", context);

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition", "inline; filename=\"protocol-" + id + ".pdf\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }

    private void addReferenceData(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("vehicleSuggestions", fieldSuggestionService.findValues(FieldSuggestionCategory.VEHICLE));
        model.addAttribute("representativeSuggestions", fieldSuggestionService.findValues(FieldSuggestionCategory.REPRESENTATIVE));
    }

    private CalibrationProtocolRequest toRequest(CalibrationProtocolFormData form) {
        return new CalibrationProtocolRequest(form.getInternalNumber(), form.getStampNumber(), form.getClientId(),
                form.getVehicleName(), form.getCardNumber(), form.getRepresentativeName(), form.getTachographBrand(),
                form.getTachographModel(), form.getTachographType(), form.getTachographManufacturer(),
                form.getPreviousInspectionDate(), form.getVehicleVrn(), form.getVehicleVin(),
                form.getTachographSerialNumber(), form.getTachographManufactureYear(), form.getInspectionReason(),
                form.getCheckMethod(), form.getMileageBefore(), form.getMileageAfter(), form.getTireSize(),
                form.getTirePressure(), form.getTireCircumferenceL(), form.getCoefficientW(), form.getConstantK(),
                form.getPathDeviationAfterInstall(), form.getPathDeviationInService(),
                form.getSpeedDeviationAfterInstall(), form.getSpeedDeviationInService(),
                form.getTimeDeviationAfterInstall(), form.getTimeDeviationInService(), form.getSpeedLimiterValue(),
                form.getCoverOpeningRegistered(), form.getPowerCutoffRegistered(),
                form.getPulseSensorInterruptionRegistered(), form.getExecutorPosition(), form.getExecutorName(),
                form.getInvoiceId());
    }

    private CalibrationProtocolFormData toFormData(CalibrationProtocolResponse response) {
        CalibrationProtocolFormData form = new CalibrationProtocolFormData();
        form.setInternalNumber(response.internalNumber());
        form.setStampNumber(response.stampNumber());
        form.setClientId(response.clientId());
        form.setVehicleName(response.vehicleName());
        form.setCardNumber(response.cardNumber());
        form.setRepresentativeName(response.representativeName());
        form.setTachographBrand(response.tachographBrand());
        form.setTachographModel(response.tachographModel());
        form.setTachographType(response.tachographType());
        form.setTachographManufacturer(response.tachographManufacturer());
        form.setPreviousInspectionDate(response.previousInspectionDate());
        form.setVehicleVrn(response.vehicleVrn());
        form.setVehicleVin(response.vehicleVin());
        form.setTachographSerialNumber(response.tachographSerialNumber());
        form.setTachographManufactureYear(response.tachographManufactureYear());
        form.setInspectionReason(response.inspectionReason());
        form.setCheckMethod(response.checkMethod());
        form.setMileageBefore(response.mileageBefore());
        form.setMileageAfter(response.mileageAfter());
        form.setTireSize(response.tireSize());
        form.setTirePressure(response.tirePressure());
        form.setTireCircumferenceL(response.tireCircumferenceL());
        form.setCoefficientW(response.coefficientW());
        form.setConstantK(response.constantK());
        form.setPathDeviationAfterInstall(response.pathDeviationAfterInstall());
        form.setPathDeviationInService(response.pathDeviationInService());
        form.setSpeedDeviationAfterInstall(response.speedDeviationAfterInstall());
        form.setSpeedDeviationInService(response.speedDeviationInService());
        form.setTimeDeviationAfterInstall(response.timeDeviationAfterInstall());
        form.setTimeDeviationInService(response.timeDeviationInService());
        form.setSpeedLimiterValue(response.speedLimiterValue());
        form.setCoverOpeningRegistered(response.coverOpeningRegistered());
        form.setPowerCutoffRegistered(response.powerCutoffRegistered());
        form.setPulseSensorInterruptionRegistered(response.pulseSensorInterruptionRegistered());
        form.setExecutorPosition(response.executorPosition());
        form.setExecutorName(response.executorName());
        form.setInvoiceId(response.invoiceId());
        return form;
    }
}
