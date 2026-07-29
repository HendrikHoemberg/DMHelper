package dev.hendrikhoemberg.dmhelper.agent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
class AgentContractControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CampaignRepository campaignRepository;

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

}
