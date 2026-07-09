package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MapPinApiController.class)
class MapPinApiControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SceneRepository sceneRepository;

    @Test
    void returnsPinnedScenesAsJson() throws Exception {
        UUID mapId = UUID.randomUUID();

        Adventure a = new Adventure();
        a.setId(UUID.randomUUID());
        a.setName("Module");

        Chapter ch = new Chapter();
        ch.setId(UUID.randomUUID());
        ch.setTitle("Ch");
        ch.setAdventure(a);

        Scene s = new Scene();
        s.setId(UUID.randomUUID());
        s.setTitle("Throne Room");
        s.setSceneKey("14");
        s.setPinX(576);
        s.setPinY(240);
        s.setChapter(ch);

        when(sceneRepository.findByMapIdAndPinXNotNull(mapId)).thenReturn(List.of(s));

        mockMvc.perform(get("/api/v1/maps/{id}/pins", mapId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sceneId").value(s.getId().toString()))
                .andExpect(jsonPath("$[0].sceneKey").value("14"))
                .andExpect(jsonPath("$[0].x").value(576))
                .andExpect(jsonPath("$[0].y").value(240))
                .andExpect(jsonPath("$[0].title").value("Throne Room"));
    }
}
