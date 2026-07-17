package dev.hendrikhoemberg.dmhelper.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentContractControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void validationErrorsArePinFree() throws Exception {
        mvc.perform(get("/api/v1/validation-errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogVersion").exists())
                .andExpect(jsonPath("$.errors").isArray());
    }
}
