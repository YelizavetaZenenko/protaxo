package com.example.protaxo.printagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Автооновлення програми друку: версія й файл — лише з правильним токеном програми. */
class AgentUpdateControllerTest {

    private final PrintAgentDistribution distribution = mock(PrintAgentDistribution.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final AgentUpdateController controller = new AgentUpdateController(distribution, audit);

    {
        ReflectionTestUtils.setField(controller, "expectedToken", "secret-token");
    }

    @Test
    void versionNeedsTheAgentToken() {
        when(distribution.version()).thenReturn(Optional.of("1.0.1"));
        assertThat(controller.version(null).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.version("wrong").getStatusCode().value()).isEqualTo(401);
        assertThat(controller.version("secret-token").getBody()).isEqualTo("1.0.1");
    }

    @Test
    void jarIsServedAndAuditedOnlyWithToken() throws IOException {
        when(distribution.available()).thenReturn(true);
        when(distribution.version()).thenReturn(Optional.of("1.0.1"));
        when(distribution.jarBytes()).thenReturn(new byte[]{1, 2, 3});

        assertThat(controller.jar("wrong", "1.0.0").getStatusCode().value()).isEqualTo(401);
        verify(audit, never()).recordAs(anyString(), any(), anyString(), any(), any());

        assertThat(controller.jar("secret-token", "1.0.0").getBody()).containsExactly(1, 2, 3);
        verify(audit).recordAs(eq("print-agent"), eq(AuditAction.FILE_DOWNLOAD), eq("PrintAgent"), isNull(), any());
    }
}
