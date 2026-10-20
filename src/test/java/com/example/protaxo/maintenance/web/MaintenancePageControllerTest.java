package com.example.protaxo.maintenance.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Після перемикання повертаємо лише на свій шлях — Referer з іншого сайту не стає open redirect. */
class MaintenancePageControllerTest {

    @Test
    void keepsPathAndQueryOfOwnPage() {
        assertThat(MaintenancePageController.localPath("https://protaxo.tail3cfdbd.ts.net/invoices?sort=date"))
                .isEqualTo("/invoices?sort=date");
    }

    @Test
    void dropsForeignHost() {
        assertThat(MaintenancePageController.localPath("https://evil.example/phish")).isEqualTo("/phish");
    }

    @Test
    void fallsBackToRootForMissingOrOddReferer() {
        assertThat(MaintenancePageController.localPath(null)).isEqualTo("/");
        assertThat(MaintenancePageController.localPath("")).isEqualTo("/");
        assertThat(MaintenancePageController.localPath("not a uri")).isEqualTo("/");
        assertThat(MaintenancePageController.localPath("https://protaxo.tail3cfdbd.ts.net")).isEqualTo("/");
        assertThat(MaintenancePageController.localPath("//evil.example/x")).isEqualTo("/x");
    }
}
