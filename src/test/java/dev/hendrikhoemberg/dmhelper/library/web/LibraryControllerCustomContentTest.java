package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSection;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LibraryController.class)
class LibraryControllerCustomContentTest {

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

    private Spell sampleSpell() {
        Spell spell = new Spell();
        spell.setId(UUID.randomUUID());
        spell.setSource(ContentSource.SRD);
        spell.setName("Fireball");
        spell.setLevel(3);
        spell.setSchool("Evocation");
        spell.setCastingTime("1 action");
        spell.setRange("150 ft");
        spell.setComponents("V, S, M");
        spell.setDuration("Instantaneous");
        spell.setDescription("<p>A bright streak flashes...</p>");
        return spell;
    }

    private Spell sampleCustomSpell() {
        Spell spell = sampleSpell();
        spell.setSource(ContentSource.CUSTOM);
        return spell;
    }

    @Test
    void spellNewFormRenders() throws Exception {
        mockMvc.perform(get("/library/spells/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("spell-form")));
    }

    @Test
    void createCustomSpellReturnsCard() throws Exception {
        Spell custom = sampleCustomSpell();
        custom.setName("Custom Fireball");
        when(spellService.createCustom(any(), any(), any())).thenReturn(custom);

        mockMvc.perform(post("/library/spells")
                        .param("name", "Custom Fireball")
                        .param("level", "3")
                        .param("school", "Evocation")
                        .param("castingTime", "1 action")
                        .param("range", "150 ft")
                        .param("components", "V, S, M")
                        .param("duration", "Instantaneous")
                        .param("description", "A bright streak flashes...")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Custom Fireball")));
    }

    @Test
    void spellDetailForCustomShowsProvenance() throws Exception {
        Spell custom = sampleCustomSpell();
        ContentProvenance prov = new ContentProvenance();
        prov.setSourceTitle("Test Source");
        prov.setLicenseClassification(dev.hendrikhoemberg.dmhelper.library.data.LicenseClassification.ORIGINAL);
        custom.setProvenance(prov);
        when(spellService.findById(custom.getId())).thenReturn(custom);

        mockMvc.perform(get("/library/spells/{id}", custom.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("provenance")));
    }

    @Test
    void spellDetailForSrdShowsSrdBadge() throws Exception {
        Spell srd = sampleSpell();
        when(spellService.findById(srd.getId())).thenReturn(srd);

        mockMvc.perform(get("/library/spells/{id}", srd.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SRD")));
    }

    @Test
    void editCustomSpellRendersForm() throws Exception {
        Spell custom = sampleCustomSpell();
        when(spellService.findById(custom.getId())).thenReturn(custom);

        mockMvc.perform(get("/library/spells/{id}/edit", custom.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("spell-form")));
    }

    @Test
    void deleteCustomSpellReturnsOk() throws Exception {
        mockMvc.perform(delete("/library/spells/{id}", UUID.randomUUID())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void cloneSpellReturnsDetail() throws Exception {
        Spell srd = sampleSpell();
        Spell cloned = sampleCustomSpell();
        when(spellService.findById(srd.getId())).thenReturn(srd);
        when(spellService.cloneAsCustom(any(), any(), any())).thenReturn(cloned);

        mockMvc.perform(post("/library/spells/{id}/clone", srd.getId())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void promoteSpellReturnsCard() throws Exception {
        Spell custom = sampleCustomSpell();
        when(spellService.findById(custom.getId())).thenReturn(custom);
        when(spellService.promoteToGlobal(custom.getId())).thenReturn(custom);

        mockMvc.perform(put("/library/spells/{id}/promote", custom.getId())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void conditionNewFormRenders() throws Exception {
        mockMvc.perform(get("/library/conditions/new"))
                .andExpect(status().isOk());
    }

    @Test
    void createCustomConditionReturnsResult() throws Exception {
        Condition c = new Condition();
        c.setId(UUID.randomUUID());
        c.setSource(ContentSource.CUSTOM);
        c.setName("Test Condition");
        when(conditionService.createCustom(any(), any(), any())).thenReturn(c);

        mockMvc.perform(post("/library/conditions")
                        .param("name", "Test Condition")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Condition")));
    }

    @Test
    void ruleNewFormRenders() throws Exception {
        mockMvc.perform(get("/library/rules/new"))
                .andExpect(status().isOk());
    }

    @Test
    void equipmentNewFormRenders() throws Exception {
        mockMvc.perform(get("/library/equipment/new"))
                .andExpect(status().isOk());
    }

    @Test
    void magicItemNewFormRenders() throws Exception {
        mockMvc.perform(get("/library/magic-items/new"))
                .andExpect(status().isOk());
    }

    @Test
    void classNewFormRenders() throws Exception {
        mockMvc.perform(get("/library/classes/new"))
                .andExpect(status().isOk());
    }

    @Test
    void speciesNewFormRenders() throws Exception {
        mockMvc.perform(get("/library/species/new"))
                .andExpect(status().isOk());
    }

    @Test
    void backgroundNewFormRenders() throws Exception {
        mockMvc.perform(get("/library/backgrounds/new"))
                .andExpect(status().isOk());
    }

    @Test
    void featNewFormRenders() throws Exception {
        mockMvc.perform(get("/library/feats/new"))
                .andExpect(status().isOk());
    }

    @Test
    void magicItemDetailForCustomShowsProvenance() throws Exception {
        MagicItem item = new MagicItem();
        item.setId(UUID.randomUUID());
        item.setSource(ContentSource.CUSTOM);
        item.setName("Custom Item");
        ContentProvenance prov = new ContentProvenance();
        prov.setSourceTitle("Test");
        item.setProvenance(prov);
        when(magicItemService.findById(item.getId())).thenReturn(item);

        mockMvc.perform(get("/library/magic-items/{id}", item.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("provenance")));
    }

    @Test
    void conditionDetailForCustomShowsProvenance() throws Exception {
        Condition c = new Condition();
        c.setId(UUID.randomUUID());
        c.setSource(ContentSource.CUSTOM);
        c.setName("Custom Condition");
        ContentProvenance prov = new ContentProvenance();
        prov.setSourceTitle("Test");
        c.setProvenance(prov);
        when(conditionService.findById(c.getId())).thenReturn(c);

        mockMvc.perform(get("/library/conditions/{id}", c.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("provenance")));
    }

    @Test
    void ruleDetailShowsSrdBadge() throws Exception {
        RuleSection r = new RuleSection();
        r.setId(UUID.randomUUID());
        r.setSource(ContentSource.SRD);
        r.setName("Custom Rule");
        when(ruleSectionService.findById(r.getId())).thenReturn(r);

        mockMvc.perform(get("/library/rules/{id}", r.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SRD")));
    }

    @Test
    void equipmentDetailForCustomShowsProvenance() throws Exception {
        EquipmentItem item = new EquipmentItem();
        item.setId(UUID.randomUUID());
        item.setSource(ContentSource.CUSTOM);
        item.setName("Custom Item");
        item.setCategory(EquipmentItem.Category.GEAR);
        ContentProvenance prov = new ContentProvenance();
        prov.setSourceTitle("Test");
        item.setProvenance(prov);
        when(equipmentItemService.findById(item.getId())).thenReturn(item);

        mockMvc.perform(get("/library/equipment/{id}", item.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("provenance")));
    }

    @Test
    void deleteCustomConditionReturnsOk() throws Exception {
        mockMvc.perform(delete("/library/conditions/{id}", UUID.randomUUID())
                        .header("HX-Request", "true"))
                .andExpect(status().isOk());
    }
}
