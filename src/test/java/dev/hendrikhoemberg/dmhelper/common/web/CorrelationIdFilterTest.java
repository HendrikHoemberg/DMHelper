package dev.hendrikhoemberg.dmhelper.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class CorrelationIdFilterTest {

    @MockitoBean
    private CampaignRepository campaignRepository;

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAndReturnsAnIdWhenTheRequestHasNone() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/roll");
        var response = new MockHttpServletResponse();
        var inside = new AtomicReference<String>();
        FilterChain chain = (req, res) -> inside.set(MDC.get(CorrelationIdFilter.ATTRIBUTE));

        filter.doFilter(request, response, chain);

        assertThat(inside.get()).matches("[0-9a-f-]{36}");
        assertThat(request.getAttribute(CorrelationIdFilter.ATTRIBUTE)).isEqualTo(inside.get());
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(inside.get());
        assertThat(MDC.get(CorrelationIdFilter.ATTRIBUTE)).isNull();
    }

    @Test
    void preservesAValidCallerId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/campaigns");
        request.addHeader(CorrelationIdFilter.HEADER, "browser-test-1234");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("browser-test-1234");
    }

    @Test
    void replacesAnInvalidCallerId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/campaigns");
        request.addHeader(CorrelationIdFilter.HEADER, "../../secret\nvalue");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .matches("[0-9a-f-]{36}")
                .doesNotContain("secret");
    }

    @Test
    void clearsMdcWhenTheChainThrows() {
        var request = new MockHttpServletRequest("GET", "/explode");
        var response = new MockHttpServletResponse();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> filter.doFilter(request, response,
                        (req, res) -> { throw new IllegalStateException("boom"); }));

        assertThat(MDC.get(CorrelationIdFilter.ATTRIBUTE)).isNull();
    }
}
