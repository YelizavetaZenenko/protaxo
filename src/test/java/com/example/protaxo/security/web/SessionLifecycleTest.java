package com.example.protaxo.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** Вихід після закриття всіх вкладок: скорочення сесії, порядок beacon/пульсу, звичайний таймаут. */
class SessionLifecycleTest {

    private final SessionLifecycleController controller = new SessionLifecycleController(Duration.ofSeconds(10));
    private final SessionTimeoutResetFilter filter = new SessionTimeoutResetFilter(Duration.ofSeconds(60));

    @Test
    void closingLastTabShortensSession() {
        MockHttpSession session = session(60);
        controller.ping(1_000L, requestWith(session));

        controller.closing(5_000L, requestWith(session));

        assertThat(session.getMaxInactiveInterval()).isEqualTo(10);
    }

    @Test
    void beaconArrivingAfterNewPagePulseIsIgnored() {
        MockHttpSession session = session(60);
        // F5: нова сторінка стартувала (6000) пізніше за pagehide старої (5000), і її пульс дійшов першим.
        controller.ping(6_000L, requestWith(session));

        controller.closing(5_000L, requestWith(session));

        assertThat(session.getMaxInactiveInterval()).isEqualTo(60);
    }

    @Test
    void pulseAfterBeaconRestoresNormalTimeout() throws Exception {
        MockHttpSession session = session(60);
        controller.closing(5_000L, requestWith(session));
        assertThat(session.getMaxInactiveInterval()).isEqualTo(10);

        filter.doFilter(requestWith(session), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(session.getMaxInactiveInterval()).isEqualTo(60);
    }

    @Test
    void filterDoesNotCreateSessionForAnonymousRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(request.getSession(false)).isNull();
    }

    private MockHttpSession session(int timeoutSeconds) {
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval(timeoutSeconds);
        return session;
    }

    private MockHttpServletRequest requestWith(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }
}
