package com.example.protaxo.maintenance.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.protaxo.maintenance.service.MaintenanceModeService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Хто проходить під час технічних робіт, а кому показується сторінка (docs/Технічні роботи.md). */
class MaintenanceModeInterceptorTest {

    private final MaintenanceModeService service = mock(MaintenanceModeService.class);
    private final MaintenanceModeInterceptor interceptor = new MaintenanceModeInterceptor(service);

    private static MockHttpServletRequest request(String uri, String... roles) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        for (String role : roles) {
            request.addUserRole(role);
        }
        return request;
    }

    @Test
    void everyonePassesWhenMaintenanceIsOff() throws Exception {
        when(service.isEnabled()).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("/invoices", "MASTER"), response, null)).isTrue();
        assertThat(response.getForwardedUrl()).isNull();
    }

    @Test
    void adminIsShownMaintenancePageWhileItIsOn() throws Exception {
        when(service.isEnabled()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("/invoices", "ADMIN"), response, null)).isFalse();
        assertThat(response.getForwardedUrl()).isEqualTo("/maintenance");
    }

    @Test
    void developerWorksAsUsualWhileItIsOn() throws Exception {
        when(service.isEnabled()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("/invoices", "ADMIN", "DEVELOPER"), response, null)).isTrue();
        assertThat(response.getForwardedUrl()).isNull();
    }

    @Test
    void apiCallsGet503JsonInsteadOfThePage() throws Exception {
        when(service.isEnabled()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("/api/vehicle-makes", "MASTER"), response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("Тривають технічні роботи");
        assertThat(response.getForwardedUrl()).isNull();
    }
}
