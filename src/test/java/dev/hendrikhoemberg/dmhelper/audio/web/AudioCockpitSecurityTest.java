package dev.hendrikhoemberg.dmhelper.audio.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "dmhelper.pin-enabled=true")
class AudioCockpitSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void getStateIsPinGated() throws Exception {
        mvc.perform(get("/api/v1/campaigns/{campaignId}/audio/runtime/state", UUID.randomUUID())
                        .param("sessionId", UUID.randomUUID().toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void muteIsPinGated() throws Exception {
        mvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/mute", UUID.randomUUID())
                        .param("sessionId", UUID.randomUUID().toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void unmuteIsPinGated() throws Exception {
        mvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/unmute", UUID.randomUUID())
                        .param("sessionId", UUID.randomUUID().toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void setOverrideIsPinGated() throws Exception {
        mvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/override", UUID.randomUUID())
                        .param("sessionId", UUID.randomUUID().toString())
                        .param("cueId", UUID.randomUUID().toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void clearOverrideIsPinGated() throws Exception {
        mvc.perform(delete("/api/v1/campaigns/{campaignId}/audio/runtime/override", UUID.randomUUID())
                        .param("sessionId", UUID.randomUUID().toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void confirmIsPinGated() throws Exception {
        mvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/confirm", UUID.randomUUID())
                        .param("sessionId", UUID.randomUUID().toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void declineIsPinGated() throws Exception {
        mvc.perform(post("/api/v1/campaigns/{campaignId}/audio/runtime/decline", UUID.randomUUID())
                        .param("sessionId", UUID.randomUUID().toString()))
                .andExpect(status().is4xxClientError());
    }
}
