package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every "+ New ..." control on /library is an ordinary link, so each form route has to answer
 * with a whole document — a bare fragment reaches the browser without any stylesheet.
 */
@WebMvcTest(LibraryController.class)
class LibraryFormPageTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private StatBlockService service;
    @MockitoBean private SpellService spellService;
    @MockitoBean private ConditionService conditionService;
    @MockitoBean private RuleSectionService ruleSectionService;
    @MockitoBean private EquipmentItemService equipmentItemService;
    @MockitoBean private MagicItemService magicItemService;
    @MockitoBean private CharacterClassService characterClassService;
    @MockitoBean private SpeciesService speciesService;
    @MockitoBean private BackgroundService backgroundService;
    @MockitoBean private FeatService featService;
    @MockitoBean private CustomContentSupport customContentSupport;
    @MockitoBean private CampaignRepository campaignRepository;

    @ParameterizedTest
    @CsvSource({
            "/library/statblocks/new,   New Statblock",
            "/library/spells/new,       New Spell",
            "/library/conditions/new,   New Condition",
            "/library/rules/new,        New Rule",
            "/library/equipment/new,    New Equipment",
            "/library/magic-items/new,  New Magic Item",
            "/library/classes/new,      New Class",
            "/library/species/new,      New Species",
            "/library/backgrounds/new,  New Background",
            "/library/feats/new,        New Feat"
    })
    void newFormIsAWholeStyledPage(String path, String title) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("stylesheet")))
                .andExpect(content().string(containsString(title)));
    }

    @Test
    void editFormIsAWholeStyledPageCarryingTheMethodOverride() throws Exception {
        Condition condition = new Condition();
        condition.setId(UUID.randomUUID());
        condition.setSource(ContentSource.CUSTOM);
        condition.setName("Waterlogged");
        when(conditionService.findById(condition.getId())).thenReturn(condition);

        mockMvc.perform(get("/library/conditions/{id}/edit", condition.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("stylesheet")))
                .andExpect(content().string(containsString("Edit Waterlogged")))
                .andExpect(content().string(containsString("name=\"_method\"")));
    }

    /** A browser can only POST the edit form, so the override has to reach the @PutMapping. */
    @Test
    void postingTheEditFormWithTheOverrideReachesTheUpdateHandler() throws Exception {
        Condition condition = new Condition();
        condition.setId(UUID.randomUUID());
        condition.setSource(ContentSource.CUSTOM);
        condition.setName("Waterlogged");
        when(conditionService.updateCustom(eq(condition.getId()), any(), any())).thenReturn(condition);

        mockMvc.perform(post("/library/conditions/{id}", condition.getId())
                        .param("_method", "put")
                        .param("name", "Waterlogged")
                        .param("description", "Soaked through."))
                .andExpect(status().isOk());

        verify(conditionService).updateCustom(eq(condition.getId()), any(), any());
    }
}
