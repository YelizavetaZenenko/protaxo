package com.example.protaxo.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Повернення на сторінку після повторного входу: лише безпечні шляхи свого сайту. */
class LoginReturnPathTest {

    @Test
    void keepsOrdinaryPagesWithQuery() {
        assertThat(SecurityConfig.safeReturnPath("/invoices/42")).isEqualTo("/invoices/42");
        assertThat(SecurityConfig.safeReturnPath("/finance/settlement?client=7")).isEqualTo("/finance/settlement?client=7");
    }

    @Test
    void rejectsForeignAndServicePaths() {
        assertThat(SecurityConfig.safeReturnPath(null)).isNull();
        assertThat(SecurityConfig.safeReturnPath("https://evil.example")).isNull();
        assertThat(SecurityConfig.safeReturnPath("//evil.example")).isNull();
        assertThat(SecurityConfig.safeReturnPath("/" + '\\' + "evil.example")).isNull();
        assertThat(SecurityConfig.safeReturnPath("/session/ping?start=1")).isNull();
        assertThat(SecurityConfig.safeReturnPath("/login?expired")).isNull();
        assertThat(SecurityConfig.safeReturnPath("/api/vehicle-makes")).isNull();
        assertThat(SecurityConfig.safeReturnPath("/")).isNull();
    }
}
