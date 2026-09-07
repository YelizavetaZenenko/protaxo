package com.example.protaxo.invoice.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class InvoiceItemFormData {

    private Long catalogItemId;

    private BigDecimal quantity;

    private BigDecimal price;
}
