package dev.hendrikhoemberg.dmhelper.session;

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
class SessionCockpitSecurityTest {

    @Autowired private MockMvc mvc;

    @Test
    void presentationRouteIsPinGated() throws Exception {
        mvc.perform(put("/api/v1/campaigns/{id}/table/presentation", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"mode\":\"CURTAIN\",\"ref\":\"\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void playerStateRouteRemainsAccessible() throws Exception {
        mvc.perform(get("/api/v1/table/state"))
                .andExpect(status().isOk());
    }
}
