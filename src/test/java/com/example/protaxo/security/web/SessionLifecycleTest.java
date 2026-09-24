package com.example.protaxo.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** Вихід після закриття всіх вкладок: скорочення сесії й повернення звичайного таймауту. */
class SessionLifecycleTest {

    private final SessionLifecycleController controller = new SessionLifecycleController(Duration.ofSeconds(20));
    private final SessionTimeoutResetFilter filter = new SessionTimeoutResetFilter(Duration.ofMinutes(3));

    @Test
    void closingLastTabShortensSession() {
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(180);

        controller.closing(requestWith(session));

        assertThat(session.getMaxInactiveInterval()).isEqualTo(20);
    }

    @Test
    void nextRequestAfterNavigationRestoresNormalTimeout() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(20);

        filter.doFilter(requestWith(session), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.getMaxInactiveInterval()).isEqualTo(180);
    }

    @Test
    void filterDoesNotCreateSessionForAnonymousRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getSession(false)).isNull();
    }

    private MockHttpServletRequest requestWith(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }
}
