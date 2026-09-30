package com.example.protaxo.security.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Фонові запити без сесії (пульс, /api/*) — 302 на /login, без WWW-Authenticate: Basic,
 * інакше браузер показує власне вікно "Sign in" (docs/Безпека і ролі.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExpiredSessionResponseTest {

    @Autowired MockMvc mvc;

    @Test
    void sessionPingWithoutSessionRedirectsToLoginWithoutBasicChallenge() throws Exception {
        mvc.perform(get("/session/ping").param("start", "1").header("Accept", "*/*"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void apiFetchWithoutSessionRedirectsToLoginWithoutBasicChallenge() throws Exception {
        mvc.perform(get("/api/vehicle-makes").header("Accept", "application/json"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }
}
