package com.example.protaxo.catalog.dto;

import com.example.protaxo.catalog.entity.CatalogItemType;
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

    private BigDecimal stockQuantity;
}
