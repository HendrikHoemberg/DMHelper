package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.common.config.PinManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "dmhelper.pin-enabled=true")
class PlayerSafeProjectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PinManager pinManager;

    @Test
    void playerViewDoesNotLeakAdventureData() throws Exception {
        mockMvc.perform(get("/player"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("adventure"))))
                .andExpect(content().string(not(containsString("scene"))));
    }

    @Test
    void pinApiReturns403WithoutValidPin() throws Exception {
        mockMvc.perform(get("/api/v1/maps/{id}/pins", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void pinApiReturns200WithValidPin() throws Exception {
        mockMvc.perform(get("/api/v1/maps/{id}/pins", UUID.randomUUID())
                        .cookie(new Cookie("dm_pin", pinManager.getPin())))
                .andExpect(status().isOk());
    }
}
