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
                "data-category=\"INSPECTION_REASON\"", "value=\"0.1\"", "value=\"0.1 хвилин за добу\"",
                "value=\"0.7 хвилин за 7 діб\"", "Цілісність пломб", "Табличка перевірки");
        // Форма закривається лише після кнопок — поля не випадають за її межі
        assertThat(html.lastIndexOf("</form>")).isGreaterThan(html.indexOf("id=\"saveProtocolBtn\""));
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
        assertThat(saved.getPowerCutoffRegistered()).isEqualTo("Так");
        assertThat(saved.getPulseSensorInterruptionRegistered()).isNull();
        assertThat(saved.isSealsIntact()).isTrue();
        assertThat(saved.isMarkPresent()).isFalse();

        String edit = mvc.perform(get("/calibration-protocols/" + saved.getId() + "/edit").with(authentication(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(edit).containsPattern("id=\"sealsIntact\"[^>]*checked");
        assertThat(edit).containsPattern("id=\"powerCutoffRegistered\"[^>]*checked");

        // Бланк з п. 14–17 рендериться; копія — в target/ для перегляду очима
        byte[] pdf = mvc.perform(get("/calibration-protocols/" + saved.getId() + "/pdf").with(authentication(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        Files.write(Path.of("target", "calibration-protocol-test.pdf"), pdf);
    }
}
