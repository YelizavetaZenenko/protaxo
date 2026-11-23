package com.example.protaxo.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.protaxo.catalog.dto.CatalogItemResponse;
import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.entity.CatalogUnit;
import com.example.protaxo.common.vat.VatRate;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Колонки каталогу «Різниця» і «Ціна з ПДВ 20%». */
class CatalogItemMarginTest {

    private static CatalogItemResponse item(String sale, String purchase) {
        return new CatalogItemResponse(1L, CatalogItemType.MATERIAL, "Пломба", sale == null ? null : new BigDecimal(sale),
                null, VatRate.NO_VAT, purchase == null ? null : new BigDecimal(purchase), CatalogUnit.PIECE, null);
    }

    @Test
    void marginAndMarkup() {
        CatalogItemResponse i = item("350.00", "250.00");
        assertThat(i.margin()).isEqualByComparingTo("100.00");
        assertThat(i.marginPercent()).isEqualByComparingTo("40");
        assertThat(i.priceWithVat20()).isEqualByComparingTo("420.00");
    }

    @Test
    void lossIsNegative() {
        assertThat(item("200.00", "250.00").margin()).isEqualByComparingTo("-50.00");
        assertThat(item("200.00", "250.00").marginPercent()).isEqualByComparingTo("-20");
    }

    @Test
    void missingPricesGiveDash() {
        assertThat(item("0.00", "250.00").margin()).isNull();
        assertThat(item("0.00", "250.00").priceWithVat20()).isNull();
        assertThat(item("350.00", null).margin()).isNull();
        assertThat(item("350.00", null).priceWithVat20()).isEqualByComparingTo("420.00");
        assertThat(item("350.00", "0.00").marginPercent()).isNull();
    }

    @Test
    void vatPriceRoundsToKopecks() {
        assertThat(item("33.33", null).priceWithVat20()).isEqualByComparingTo("40.00");
        assertThat(item("10.05", null).priceWithVat20()).isEqualByComparingTo("12.06");
    }
}
