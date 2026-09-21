package com.example.protaxo.catalog.dto;

import com.example.protaxo.catalog.entity.CatalogItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CatalogItemFormData {

    @NotNull(message = "Оберіть тип")
    private CatalogItemType type;

    @NotBlank(message = "Назва обов'язкова")
    private String name;

    @NotNull(message = "Вкажіть базову ціну")
    private BigDecimal basePrice;

    @DecimalMin(value = "0.000", message = "Залишок має бути невід'ємним числом")
    private BigDecimal stockQuantity;
}
