package com.example.protaxo.invoice.dto;

import com.example.protaxo.invoice.entity.InvoicePaymentType;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

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

    private List<InvoiceItemFormData> items = new ArrayList<>();
}
