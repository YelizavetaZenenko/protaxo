package com.example.protaxo.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.protaxo.client.dto.ClientResponse;
import com.example.protaxo.contract.dto.ContractResponse;
import com.example.protaxo.finance.entity.FinanceSettings;
import com.example.protaxo.pdf.config.PdfTemplateConfig;
import com.example.protaxo.pdf.service.PdfRenderService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Договір рендериться без бази: реквізити замовника (V71) і Додаток №1 — бланк акту виконаних
 * робіт. Щоб подивитися PDF: {@code -Dcontract.pdf.out=шлях/до/файлу.pdf}.
 */
class ContractPdfRenderTest {

    private final TemplateEngine engine = new PdfTemplateConfig().pdfTemplateEngine();
    private final PdfRenderService renderer = new PdfRenderService(engine);

    private static ClientResponse client(boolean withRequisites) {
        return new ClientResponse(1L, "Карпатський Перевізник",
                "44556677", "Товариство з обмеженою відповідальністю \"Карпатський Перевізник\"",
                null, "+38(067)-123-45-67", "445566771234", "Іваненко", "Петро", "Васильович",
                null, null, null, null, null, null, false,
                withRequisites ? "79000, м. Львів, вул. Городоцька, 15" : null,
                withRequisites ? "UA213223130000026007233566001" : null,
                withRequisites ? "АТ КБ «ПриватБанк»" : null,
                withRequisites ? "305299" : null,
                withRequisites ? "Директор" : null,
                withRequisites ? "Статуту" : null,
                null, null, null, null, null, null, null);
    }

    private String html(ClientResponse client) {
        return engine.process("contract", context(client));
    }

    private Context context(ClientResponse client) {
        Context context = new Context(Locale.forLanguageTag("uk"));
        context.setVariable("contract", new ContractResponse(3L, 1L, "000003", LocalDate.of(2026, 9, 7)));
        context.setVariable("client", client);
        context.setVariable("settings", new FinanceSettings());
        return context;
    }

    @Test
    void requisitesAndSignatoryComeFromTheClient() {
        String html = html(client(true));
        assertThat(html)
                .contains("в особі", "директор", "Іваненко Петро Васильович", "на підставі", "Статуту")
                .contains("79000, м. Львів, вул. Городоцька, 15", "UA213223130000026007233566001",
                        "АТ КБ «ПриватБанк»", "МФО:", "305299", "Код ЄДРПОУ:", "44556677", "ІПН:", "445566771234")
                .contains("Директор", "Іваненко П.В.");
    }

    @Test
    void emptyRequisitesStayBlankLines() {
        String html = html(client(false));
        assertThat(html).contains("val blank").doesNotContain("UA2132");
    }

    @Test
    void appendixIsABlankActNotAnOrderForm() throws IOException {
        String html = html(client(true));
        assertThat(html)
                .contains("Додаток №1 до договору № 000003 від 07.09.2026 р.")
                .contains("АКТ ВИКОНАНИХ РОБІТ З ТЕХНІЧНОГО ОБСЛУГОВУВАННЯ І РЕМОНТУ ТРАНСПОРТНОГО ЗАСОБУ")
                .contains("НАДАНІ МАТЕРІАЛИ", "НАДАНІ ПОСЛУГИ", "Умови гарантії", "Від замовника")
                .doesNotContain("ЗАМОВЛЕННЯ-НАРЯД (зразок)");

        byte[] pdf = renderer.render("contract", context(client(true)));
        assertThat(pdf).isNotEmpty();
        String out = System.getProperty("contract.pdf.out");
        if (out != null) {
            Files.write(Path.of(out), pdf);
            // Той самий договір із незаповненими реквізитами замовника — як виглядають лінії.
            Files.write(Path.of(out.replace(".pdf", "-empty.pdf")), renderer.render("contract", context(client(false))));
        }
    }
}
