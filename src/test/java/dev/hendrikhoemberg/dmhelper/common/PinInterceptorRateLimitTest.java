package dev.hendrikhoemberg.dmhelper.common;

import dev.hendrikhoemberg.dmhelper.common.config.PinInterceptor;
import dev.hendrikhoemberg.dmhelper.common.config.PinManager;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class PinInterceptorRateLimitTest {

    private PinManager pinManager;
    private PinInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        pinManager = new PinManager();
        interceptor = new PinInterceptor(pinManager);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        response = new MockHttpServletResponse();
    }

    @Test
    void deniesFirstRequestWithoutPin() throws Exception {
        assertThat(interceptor.preHandle(request, response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void returns429AfterTenFailedAttempts() throws Exception {
        for (int i = 0; i < 10; i++) {
            interceptor.preHandle(request, response, null);
            response = new MockHttpServletResponse();
        }
        assertThat(interceptor.preHandle(request, response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void resetsAfterSuccessfulAuthentication() throws Exception {
        for (int i = 0; i < 5; i++) {
            interceptor.preHandle(request, response, null);
            response = new MockHttpServletResponse();
        }
        request.setCookies(new Cookie("dm_pin", pinManager.getPin()));
        assertThat(interceptor.preHandle(request, response, null)).isTrue();
        response = new MockHttpServletResponse();
        request.setCookies(null);
        assertThat(interceptor.preHandle(request, response, null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }
}
