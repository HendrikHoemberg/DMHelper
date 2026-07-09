package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "dmhelper.pin-enabled=false")
class MapPinAccessControlTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SceneRepository sceneRepository;

    @Test
    void returnsPinsSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/maps/{id}/pins", UUID.randomUUID()))
                .andExpect(status().isOk());
    }
}
