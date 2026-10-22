package com.example.protaxo.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.protaxo.calibration.entity.CalibrationProtocol;
import com.example.protaxo.calibration.repository.CalibrationProtocolRepository;
import com.example.protaxo.client.entity.Client;
import com.example.protaxo.client.repository.ClientRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Форма протоколу рендериться; підтеми "Відхилення" і "Реєстрування факту" зберігаються. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CalibrationProtocolFormPageTest {

    @Autowired MockMvc mvc;
    @Autowired ClientRepository clientRepository;
    @Autowired CalibrationProtocolRepository calibrationProtocolRepository;

    UsernamePasswordAuthenticationToken admin = new UsernamePasswordAuthenticationToken(
            "admin@protaxo.local", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(admin);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void newFormRendersWithDefaultDeviationsAndFactChecks() throws Exception {
        String html = mvc.perform(get("/calibration-protocols/new").with(authentication(admin))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Рік виготовлення", "Відхилення</h2>", "Реєстрування факту</h2>",
                "data-category=\"INSPECTION_REASON\"", "data-category=\"TIRE_SIZE\"", "value=\"0.1\"", "value=\"+0.01 та +0.07\"",
                "value=\"0.7 хвилин за 7 діб\"", "Цілісність пломб", "Табличка перевірки",
                "<option value=\"0.20\">2,0 бар (0.20 МПа)</option>", "<option value=\"0.95\">9,5 бар (0.95 МПа)</option>");
        Files.writeString(Path.of("target", "calibration-protocol-form-test.html"), html);
        // Відхилення шляху/швидкості "в експлуатації" на сайті не вводяться — у бланку завжди "-"
        assertThat(html).doesNotContain("id=\"pathDeviationInService\"", "id=\"speedDeviationInService\"", "id=\"timeDeviationInService\"");
        // Форма закривається лише після кнопок — поля не випадають за її межі
        assertThat(html.lastIndexOf("</form>")).isGreaterThan(html.indexOf("id=\"saveProtocolBtn\""));
    }

    @Test
    void tireSizesGoFromSmallestRimToBiggest() throws Exception {
        String json = mvc.perform(get("/api/tachograph-attributes").param("category", "TIRE_SIZE").with(authentication(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.indexOf("195/70 R15C")).isLessThan(json.indexOf("185/75 R16C"));
        assertThat(json.indexOf("185/75 R16C")).isLessThan(json.indexOf("205/75 R17,5"));
        assertThat(json.indexOf("315/70 R22,5")).isLessThan(json.indexOf("445/65 R22,5"));
        assertThat(json.indexOf("445/65 R22,5")).isLessThan(json.indexOf("12 R24"));
    }

    @Test
    void internalCommentIsSavedSeparatelyAndStandaloneProtocolIsHighlighted() throws Exception {
        Client client = clientRepository.save(Client.builder().name("Коментар клієнт " + UUID.randomUUID()).build());
        String internalNumber = "C-" + UUID.randomUUID();
        mvc.perform(post("/calibration-protocols").with(authentication(admin)).with(csrf())
                        .param("internalNumber", internalNumber)
                        .param("clientId", client.getId().toString())
                        .param("cardNumber", "ABC123456789012"))
                .andExpect(status().is3xxRedirection());
        CalibrationProtocol saved = calibrationProtocolRepository.findAll().stream()
                .filter(p -> internalNumber.equals(p.getInternalNumber())).findFirst().orElseThrow();

        mvc.perform(post("/calibration-protocols/" + saved.getId() + "/comment").with(authentication(admin)).with(csrf())
                        .param("internalComment", "  Клієнт просив передзвонити  "))
                .andExpect(status().is3xxRedirection());
        assertThat(calibrationProtocolRepository.findById(saved.getId()).orElseThrow().getInternalComment())
                .isEqualTo("Клієнт просив передзвонити");

        // Редагування протоколу коментар не затирає
        mvc.perform(post("/calibration-protocols/" + saved.getId() + "/edit").with(authentication(admin)).with(csrf())
                        .param("internalNumber", internalNumber)
                        .param("clientId", client.getId().toString())
                        .param("cardNumber", "ABC123456789012"))
                .andExpect(status().is3xxRedirection());
        assertThat(calibrationProtocolRepository.findById(saved.getId()).orElseThrow().getInternalComment())
                .isEqualTo("Клієнт просив передзвонити");

        String view = mvc.perform(get("/calibration-protocols/" + saved.getId()).with(authentication(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // Автор — ПІБ з профілю (або email, якщо користувача/ПІБ немає)
        CalibrationProtocol commented = calibrationProtocolRepository.findById(saved.getId()).orElseThrow();
        String author = commented.getInternalCommentAuthor();
        assertThat(author).isNotBlank();
        assertThat(commented.getInternalCommentUpdatedAt()).isNotNull();
        assertThat(view).contains("Внутрішній коментар", "Клієнт просив передзвонити", "Створено без наряд-заказу",
                author, java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));

        String list = mvc.perform(get("/calibration-protocols").with(authentication(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Files.writeString(Path.of("target", "calibration-protocol-view-test.html"), view);
        Files.writeString(Path.of("target", "calibration-protocol-list-test.html"), list);
        String row = list.substring(list.lastIndexOf("<tr", list.indexOf(internalNumber)));
        row = row.substring(0, row.indexOf("</tr>"));
        assertThat(row).contains("protocol-row-standalone", "без наряду", "title=\"Клієнт просив передзвонити — " + author + ", ");
    }

    @Test
    void factChecksAreSavedAndPrinted() throws Exception {
        Client client = clientRepository.save(Client.builder().name("Протокол клієнт " + UUID.randomUUID()).build());
        String internalNumber = "T-" + UUID.randomUUID();
        mvc.perform(post("/calibration-protocols").with(authentication(admin)).with(csrf())
                        .param("internalNumber", internalNumber)
                        .param("clientId", client.getId().toString())
                        .param("cardNumber", "ABC123456789012")
                        .param("inspectionReason", "ремонт")
                        .param("previousInspectionDate", "05.03.2025")
                        .param("tireSize", "315/70 R22,5")
                        .param("tirePressure", "0.85")
                        .param("pathDeviationAfterInstall", "0.5")
                        .param("powerCutoffRegistered", "Так").param("_powerCutoffRegistered", "on")
                        .param("_pulseSensorInterruptionRegistered", "on")
                        .param("sealsIntact", "true").param("_sealsIntact", "on")
                        .param("_markPresent", "on")
                        .param("_verificationPlatePresent", "on")
                        .param("noManipulationDevices", "true").param("_noManipulationDevices", "on"))
                .andExpect(status().is3xxRedirection());

        CalibrationProtocol saved = calibrationProtocolRepository.findAll().stream()
                .filter(p -> internalNumber.equals(p.getInternalNumber())).findFirst().orElseThrow();
        assertThat(saved.getInspectionReason()).isEqualTo("ремонт");
        assertThat(saved.getTireSize()).isEqualTo("315/70 R22,5");
        assertThat(saved.getTirePressure()).isEqualTo("0.85");
        assertThat(saved.getPreviousInspectionDate()).isEqualTo(LocalDate.of(2025, 3, 5));
        assertThat(saved.getPowerCutoffRegistered()).isEqualTo("Так");
        assertThat(saved.getPulseSensorInterruptionRegistered()).isNull();
        assertThat(saved.isSealsIntact()).isTrue();
        assertThat(saved.isMarkPresent()).isFalse();

        String edit = mvc.perform(get("/calibration-protocols/" + saved.getId() + "/edit").with(authentication(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(edit).containsPattern("id=\"sealsIntact\"[^>]*checked");
        assertThat(edit).contains("value=\"05.03.2025\"");
        assertThat(edit).containsPattern("<option value=\"0.85\" selected=\"selected\">8,5 бар");
        assertThat(edit).containsPattern("id=\"powerCutoffRegistered\"[^>]*checked");

        // Бланк з п. 14–17 рендериться; копія — в target/ для перегляду очима
        byte[] pdf = mvc.perform(get("/calibration-protocols/" + saved.getId() + "/pdf").with(authentication(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        Files.write(Path.of("target", "calibration-protocol-test.pdf"), pdf);
    }
}
