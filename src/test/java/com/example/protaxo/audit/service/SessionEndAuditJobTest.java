package com.example.protaxo.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Причина в записі «Сесію завершено»: закриті вкладки (скорочений таймаут) чи неактивність. */
class SessionEndAuditJobTest {

    private final SessionEndAuditJob job = new SessionEndAuditJob(null, null);

    {
        ReflectionTestUtils.setField(job, "closingGrace", Duration.ofSeconds(10));
    }

    @Test
    void shortTimeoutMeansAllTabsClosed() {
        Map<String, String[]> changes = job.describe(
                new SessionEndAuditJob.Expired("id", "a@b.c", 0, 3_600_000, 10, 3_610_000), "/invoices/5");
        assertThat(changes.get("Причина")[1]).isEqualTo("Закрито всі вкладки сайту");
        assertThat(changes.get("Остання сторінка")[1]).isEqualTo("/invoices/5");
        assertThat(changes.get("Тривалість сесії")[1]).isEqualTo("1 год 0 хв");
    }

    @Test
    void fullTimeoutMeansInactivity() {
        Map<String, String[]> changes = job.describe(
                new SessionEndAuditJob.Expired("id", "a@b.c", 0, 60_000, 1800, 1_860_000), null);
        assertThat(changes.get("Причина")[1]).startsWith("Неактивність 30 хв");
        assertThat(changes).doesNotContainKey("Остання сторінка");
    }
}
