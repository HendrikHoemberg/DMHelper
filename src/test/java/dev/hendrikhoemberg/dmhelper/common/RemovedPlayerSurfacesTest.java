package dev.hendrikhoemberg.dmhelper.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The DM-only cut removed the player view, the table projection, the presentation module and
 * the QR/PIN gate. Several player-safety tests used to prove "this surface leaks nothing" by
 * fetching those endpoints and scanning the body -- assertions that now pass vacuously against
 * a 404 payload. They were deleted; this class replaces them with the assertion that actually
 * matters, so a surface cannot quietly come back without a deliberate decision.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RemovedPlayerSurfacesTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void thePlayerViewIsGone() throws Exception {
        mvc.perform(get("/player")).andExpect(status().isNotFound());
    }

    @Test
    void theTableProjectionApiIsGone() throws Exception {
        mvc.perform(get("/api/v1/table/state")).andExpect(status().isNotFound());
        mvc.perform(get("/api/table/status")).andExpect(status().isNotFound());
    }

    @Test
    void thePlayerViewQrCodeIsGone() throws Exception {
        mvc.perform(get("/qr/player-view")).andExpect(status().isNotFound());
    }
}
