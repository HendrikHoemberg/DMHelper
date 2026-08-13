package dev.hendrikhoemberg.dmhelper.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class GlobalExceptionHandlerTest {

    @MockitoBean
    private CampaignRepository campaignRepository;

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
    void htmxFailureRendersSafeCorrelatedFragment() throws Exception {
        mvc.perform(get("/explode").header("HX-Request", "true")
                        .header(CorrelationIdFilter.HEADER, "test-corr-5678"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString(
                        "The request could not be completed.")))
                .andExpect(content().string(containsString("test-corr-5678")))
                .andExpect(content().string(not(containsString("database-password"))));
    }

    @Test
    void unsupportedMethodRemainsAClientErrorAndIsCorrelated() throws Exception {
        mvc.perform(post("/explode").accept(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdFilter.HEADER, "test-corr-4050"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "test-corr-4050"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.correlationId").value("test-corr-4050"));
    }

    @Test
    void missingParameterRemainsAClientErrorAndIsCorrelated() throws Exception {
        mvc.perform(get("/required-param").accept(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdFilter.HEADER, "test-corr-4001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.correlationId").value("test-corr-4001"));
    }

    @Test
    void malformedJsonRemainsAClientErrorAndIsCorrelated() throws Exception {
        mvc.perform(post("/json").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{not-json")
                        .header(CorrelationIdFilter.HEADER, "test-corr-4002"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.correlationId").value("test-corr-4002"));
    }

    @Test
    void frameworkHtmxFailureReturnsASafeCorrelatedFragment() throws Exception {
        mvc.perform(get("/missing").header("HX-Request", "true")
                        .header(CorrelationIdFilter.HEADER, "test-corr-4040"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString(
                        "The requested item could not be found. Reload and try again.")))
                .andExpect(content().string(containsString("test-corr-4040")))
                .andExpect(content().string(not(containsString("No static resource"))));
    }

    @Test
    void sessionNotOpenIsAConflictWithAMachineReadableCode() throws Exception {
        mvc.perform(get("/session-not-open").accept(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdFilter.HEADER, "test-corr-4091"))
                .andExpect(status().isConflict())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "test-corr-4091"))
                .andExpect(jsonPath("$.title").value("Session Not Open"))
                .andExpect(jsonPath("$.detail").value("Start the session before running an encounter."))
                .andExpect(jsonPath("$.code").value("SESSION_NOT_OPEN"));
    }

    @RestController
    static class FailingController {
        @GetMapping("/explode")
        String explode() {
            throw new IllegalStateException("database-password=/private/path");
        }

        @GetMapping("/session-not-open")
        String sessionNotOpen() {
            throw new dev.hendrikhoemberg.dmhelper.session.service.SessionNotOpenException();
        }

        @GetMapping("/required-param")
        String requiredParam(@RequestParam String value) {
            return value;
        }

        @PostMapping("/json")
        String json(@RequestBody JsonInput input) {
            return input.value();
        }
    }

    record JsonInput(String value) {}
}
