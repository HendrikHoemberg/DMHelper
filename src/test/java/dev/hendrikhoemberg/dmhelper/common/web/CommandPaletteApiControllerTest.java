package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.common.service.CommandPaletteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommandPaletteApiController.class)
class CommandPaletteApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CommandPaletteService commandPaletteService;

    @Test
    void searchReturnsJsonResults() throws Exception {
        var item = new CommandPaletteService.SearchResultItem(
                "550e8400-e29b-41d4-a716-446655440000", "Goblin", "statblock",
                "Humanoid (CR 1/4)", "/library/statblocks/550e8400-e29b-41d4-a716-446655440000");
        when(commandPaletteService.search("Goblin", null, null)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/search").param("q", "Goblin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Goblin"))
                .andExpect(jsonPath("$[0].type").value("statblock"))
                .andExpect(jsonPath("$[0].url").value("/library/statblocks/550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void searchWithCampaignIdPassesItToService() throws Exception {
        var item = new CommandPaletteService.SearchResultItem(
                "abc", "The Cave", "note", "LOCATION", "/campaigns/uuid/notes/abc");
        when(commandPaletteService.search("cave", java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), null))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "cave")
                        .param("campaignId", "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("The Cave"));
    }

    @Test
    void searchWithEmptyQueryReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
