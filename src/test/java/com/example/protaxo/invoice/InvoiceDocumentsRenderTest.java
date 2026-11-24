package com.example.protaxo.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.protaxo.catalog.entity.CatalogItemType;
import com.example.protaxo.catalog.entity.CatalogUnit;
import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.common.util.UkrainianAmountWords;
import com.example.protaxo.common.vat.VatRate;
import com.example.protaxo.invoice.dto.ActSection;
import com.example.protaxo.invoice.dto.BillItemRow;
import com.example.protaxo.invoice.dto.InvoiceItemResponse;
import com.example.protaxo.invoice.dto.InvoiceResponse;
import com.example.protaxo.invoice.entity.ActStatus;
import com.example.protaxo.invoice.entity.InvoicePaymentType;
import com.example.protaxo.pdf.config.PdfTemplateConfig;
import com.example.protaxo.pdf.service.PdfRenderService;
import com.example.protaxo.vehicle.dto.VehicleResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Тестовий наряд-заказ неплатника ПДВ → рахунок і акт без рядків ПДВ, з позначкою «Без ПДВ».
 * Без бази: ті самі шаблони й ті самі змінні, що InvoicePageController. Щоб подивитися PDF:
 * {@code -Dinvoice.pdf.dir=тека}.
 */
class InvoiceDocumentsRenderTest {

    private final TemplateEngine engine = new PdfTemplateConfig().pdfTemplateEngine();
    private final PdfRenderService renderer = new PdfRenderService(engine);

    private static InvoiceItemResponse item(int line, CatalogItemType type, String name, String qty, String price) {
        BigDecimal amount = new BigDecimal(qty).multiply(new BigDecimal(price)).setScale(2);
        return new InvoiceItemResponse((long) line, line, (long) line, type, name, new BigDecimal(qty),
                new BigDecimal(price), null, amount, VatRate.NO_VAT, BigDecimal.ZERO.setScale(2), amount, CatalogUnit.PIECE);
    }

    private static final List<InvoiceItemResponse> ITEMS = List.of(
            item(1, CatalogItemType.MATERIAL, "Елемент живлення цифрового тахографа", "1", "350.00"),
            item(2, CatalogItemType.MATERIAL, "Пломба для SMART тахографа", "2", "200.00"),
            item(3, CatalogItemType.SERVICE, "Калібрування тахографа-Цифровий", "1", "1900.00"),
            item(4, CatalogItemType.SERVICE, "Пломбування датчика швидкості", "1", "200.00"));

    private static final BigDecimal TOTAL = ITEMS.stream().map(InvoiceItemResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

    private static final InvoiceResponse INVOICE = new InvoiceResponse(1L, "1", LocalDateTime.of(2026, 11, 22, 10, 0),
            InvoicePaymentType.TRANSFER, 1L, "AC1466HI (Volvo FH)", "Петренко Іван", "Вишивата Д.О.", "Вишивата Д.О.",
            null, ITEMS, TOTAL, BigDecimal.ZERO.setScale(2), TOTAL, LocalDate.of(2026, 12, 7), ActStatus.NOT_CREATED);

    private static final ClientResponse CLIENT = new ClientResponse(1L, "Волинь Зерно Продукт", "31496816",
            "СГПП «Волинь Зерно Продукт»", null, "+38(095)-995-44-16", "314968103989", "Вілія", "Юрій", "Григорович",
            null, null, null, null, null, null, false,
            "45601, Волинська обл., Луцький р-н, с. Рованці, вул. Луцька, 1", null, null, null, "Директор", "Статуту",
            null, null, null, null, null, null);

    private static BillItemRow row(InvoiceItemResponse i) {
        boolean service = i.catalogItemType() == CatalogItemType.SERVICE;
        BigDecimal priceWithoutVat = i.price().subtract(i.vatRate().vatFrom(i.price()));
        return new BillItemRow(i.lineNumber(), i.itemName(), service ? "послуга" : i.unitLabel(), i.quantity(), i.price(),
                i.discountPercent(), i.amountWithoutVat(), i.vatRate().getLabel(), i.vatAmount(), i.amount(),
                service, priceWithoutVat, i.amountWithoutVat());
    }

    /** Як InvoicePageController.documentContext для неплатника ПДВ. */
    private static Context context() {
        Context c = new Context(Locale.forLanguageTag("uk"));
        c.setVariable("invoice", INVOICE);
        c.setVariable("client", CLIENT);
        c.setVariable("totalAmount", TOTAL);
        c.setVariable("totalVat", BigDecimal.ZERO.setScale(2));
        c.setVariable("totalWithoutVat", TOTAL);
        c.setVariable("amountInWords", UkrainianAmountWords.amountToWords(TOTAL));
        c.setVariable("vatInWords", UkrainianAmountWords.amountToWords(BigDecimal.ZERO));
        c.setVariable("vatPayer", false);
        c.setVariable("contractNumber", "000001");
        c.setVariable("contractDate", LocalDate.of(2026, 11, 22));
        return c;
    }

    @Test
    void billWithoutVat() throws IOException {
        List<BillItemRow> rows = ITEMS.stream().map(InvoiceDocumentsRenderTest::row).toList();
        Context c = context();
        c.setVariable("rows", rows);
        c.setVariable("hasDiscount", false);
        String html = engine.process("invoice-bill", c);
        assertThat(html).contains("Без ПДВ").contains("Калібрування тахографа-Цифровий").doesNotContain("ПДВ (20%)");
        save("rahunok-bez-pdv.pdf", renderer.render("invoice-bill", c));
    }

    @Test
    void actWithoutVat() throws IOException {
        List<BillItemRow> rows = ITEMS.stream().map(InvoiceDocumentsRenderTest::row).toList();
        Context c = context();
        c.setVariable("materials", ActSection.of(rows.stream().filter(r -> !r.service()).toList()));
        c.setVariable("services", ActSection.of(rows.stream().filter(BillItemRow::service).toList()));
        c.setVariable("vehicle", new VehicleResponse(1L, 1L, "YV2RT40A0KB123456", "AC1466HI", null, "Volvo", "FH", 2019));
        String html = engine.process("act", c);
        assertThat(html).contains("Всього, без ПДВ").doesNotContain("ПДВ (20%)");
        save("akt-bez-pdv.pdf", renderer.render("act", c));
    }

    private static void save(String name, byte[] pdf) throws IOException {
        assertThat(pdf).isNotEmpty();
        String dir = System.getProperty("invoice.pdf.dir");
        if (dir != null) {
            Files.write(Path.of(dir, name), pdf);
        }
    }
}
