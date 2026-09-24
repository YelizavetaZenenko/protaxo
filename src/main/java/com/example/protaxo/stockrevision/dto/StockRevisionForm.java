package com.example.protaxo.stockrevision.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** Форма чернетки ревізії: фактична кількість і коментар по кожному рядку. */
@Data
public class StockRevisionForm {

    private String comment;

    private List<Line> lines = new ArrayList<>();

    @Data
    public static class Line {
        private Long id;
        private BigDecimal actualQuantity;
        private String comment;
    }
}
