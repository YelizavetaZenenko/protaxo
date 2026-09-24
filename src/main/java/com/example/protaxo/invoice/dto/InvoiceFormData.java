package com.example.protaxo.invoice.dto;

import com.example.protaxo.invoice.entity.InvoicePaymentType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class InvoiceFormData {

    @NotNull(message = "Оберіть тип оплати")
    private InvoicePaymentType paymentType;

    @NotNull(message = "Оберіть контрагента")
    private Long clientId;

    private String vehicleName;

    private String driverName;

    private String repairResponsibleName;

    private String repairSupervisorName;

    /** Строк оплати — необов'язковий; без нього наряд не може бути "простроченим". */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paymentDueDate;

    private List<InvoiceItemFormData> items = new ArrayList<>();
}
