package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LibraryApiController.class)
class LibraryApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StatBlockService statBlockService;
    @MockitoBean private SpellService spellService;
    @MockitoBean private ConditionService conditionService;
    @MockitoBean private RuleSectionService ruleSectionService;
    @MockitoBean private EquipmentItemService equipmentItemService;
    @MockitoBean private MagicItemService magicItemService;
    @MockitoBean private CharacterClassService characterClassService;
    @MockitoBean private SpeciesService speciesService;
    @MockitoBean private BackgroundService backgroundService;
    @MockitoBean private FeatService featService;

    private StatBlock srd(String key) {
        StatBlock sb = new StatBlock();
        sb.setSource(ContentSource.SRD);
        sb.setSourceKey(key);
        return sb;
    }

    @Test
    void listsSortedSrdKeysFromAllSources() throws Exception {
        when(statBlockService.findAll()).thenReturn(List.of(srd("goblin")));
        when(spellService.findAll()).thenReturn(List.of());
        when(conditionService.findAll()).thenReturn(List.of());
        when(ruleSectionService.findAll()).thenReturn(List.of());
        when(equipmentItemService.findAll()).thenReturn(List.of());
        when(magicItemService.findAll()).thenReturn(List.of());
        when(characterClassService.findAll()).thenReturn(List.of());
        when(speciesService.findAll()).thenReturn(List.of());
        when(backgroundService.findAll()).thenReturn(List.of());
        when(featService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/library/srd-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("goblin"))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
