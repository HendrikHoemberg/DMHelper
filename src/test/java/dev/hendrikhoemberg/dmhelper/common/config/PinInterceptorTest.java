package dev.hendrikhoemberg.dmhelper.common.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
    void shouldAllowPlayerRouteWithoutPin() throws Exception {
        mvc.perform(get("/player"))
                .andExpect(status().isOk());
    }
}
