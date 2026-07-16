package dev.hendrikhoemberg.dmhelper.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void unexpectedJsonFailureIsSafeAndCorrelated() throws Exception {
        mvc.perform(get("/explode").accept(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdFilter.HEADER, "test-corr-1234"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "test-corr-1234"))
                .andExpect(jsonPath("$.title").value("Request Failed"))
                .andExpect(jsonPath("$.detail").value("The request could not be completed."))
                .andExpect(jsonPath("$.correlationId").value("test-corr-1234"))
                .andExpect(content().string(not(containsString("database-password"))));
    }

    @Test
    void htmxFailureRendersSafeReference() throws Exception {
        mvc.perform(get("/explode").header("HX-Request", "true")
                        .header(CorrelationIdFilter.HEADER, "test-corr-5678"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("common/_error"))
                .andExpect(model().attribute("message", "The request could not be completed."))
                .andExpect(model().attribute("correlationId", "test-corr-5678"));
    }

    @RestController
    static class FailingController {
        @GetMapping("/explode")
        String explode() {
            throw new IllegalStateException("database-password=/private/path");
        }
    }
}
