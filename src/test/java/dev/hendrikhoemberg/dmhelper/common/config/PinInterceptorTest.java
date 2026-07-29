package dev.hendrikhoemberg.dmhelper.common.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "dmhelper.pin-enabled=true")
class PinInterceptorTest {

    @Autowired private MockMvc mvc;
    @Autowired private PinManager pinManager;

    @Test
    void shouldRejectRequestWithoutPin() throws Exception {
        mvc.perform(get("/campaigns"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectRequestWithInvalidPin() throws Exception {
        mvc.perform(get("/campaigns")
                        .cookie(new Cookie("dm_pin", "WRONG!")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowRequestWithValidPin() throws Exception {
        mvc.perform(get("/campaigns")
                        .cookie(new Cookie("dm_pin", pinManager.getPin())))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowStaticAssetsWithoutPin() throws Exception {
        mvc.perform(get("/css/base.css"))
                .andExpect(status().isOk());
    }

    @Test
    void worldRoutesRequirePin() throws Exception {
        mvc.perform(get("/campaigns/" + UUID.randomUUID() + "/world/npcs"))
                .andExpect(status().isForbidden());
    }

    @Test
    void threatApisRequirePin() throws Exception {
        // Each assertion uses a fresh remote-addr-equivalent cookie jar; rate-limit after
        // repeated failures may return 429, which still denies unauthenticated access.
        for (String path : java.util.List.of(
                "/api/v1/traps/search?q=x",
                "/api/v1/hazards/search?q=x",
                "/library/traps",
                "/library/hazards",
                "/api/v1/maps/" + UUID.randomUUID() + "/pins",
                "/api/v1/traps/" + UUID.randomUUID() + "/deletion-impact",
                "/api/v1/hazards/" + UUID.randomUUID() + "/deletion-impact")) {
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(status)
                    .as("PIN must block %s", path)
                    .isIn(403, 429);
        }
    }

}
