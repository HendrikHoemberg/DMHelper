package dev.hendrikhoemberg.dmhelper.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "dmhelper.pin-enabled=true")
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

    @Test
    void capabilitiesArePinFree() throws Exception {
        mvc.perform(get("/api/v1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestVersion").exists())
                .andExpect(jsonPath("$.capabilities").isArray());
    }

    @Test
    void schemasRemainPinFree() throws Exception {
        mvc.perform(get("/api/v1/schemas/campaign-format-v2"))
                .andExpect(status().isOk());
    }

    @Test
    void dmRoutesStillRequirePinWhenEnabled() throws Exception {
        mvc.perform(put("/api/v1/campaigns/{id}/table/presentation", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"mode\":\"CURTAIN\",\"ref\":\"\"}"))
                .andExpect(status().isForbidden());
    }
}
