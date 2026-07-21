package dev.hendrikhoemberg.dmhelper.threat.web;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheck;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheckMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReference;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReferenceRole;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapDisarmMethod;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.service.HazardService;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatDeletionImpact;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatReferenceResolver;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatValidationException;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatValidationProblem;
import dev.hendrikhoemberg.dmhelper.threat.service.TrapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;

@WebMvcTest(ThreatApiController.class)
class ThreatApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrapService trapService;

    @MockitoBean
    private HazardService hazardService;

    @MockitoBean
    private CustomContentSupport customContentSupport;

    @MockitoBean
    private ThreatReferenceResolver referenceResolver;

    @MockitoBean
    private ConditionRepository conditionRepository;

    @MockitoBean
    private EquipmentItemRepository equipmentItemRepository;

    @MockitoBean
    private MagicItemRepository magicItemRepository;

    @MockitoBean
    private StatBlockRepository statBlockRepository;

    @MockitoBean
    private CampaignRepository campaignRepository;

    private final UUID campaignId = UUID.randomUUID();

    private Trap trap(UUID id, String name) {
        Trap t = new Trap();
        t.setId(id);
        t.setSourceKey(name.toLowerCase().replace(' ', '-'));
        t.setName(name);
        t.setSource(ContentSource.CUSTOM);
        t.setSeverity(ThreatSeverity.DANGEROUS);
        t.setResetMode(ThreatResetMode.NONE);
        return t;
    }

    private Hazard hazard(UUID id, String name) {
        Hazard h = new Hazard();
        h.setId(id);
        h.setSourceKey(name.toLowerCase().replace(' ', '-'));
        h.setName(name);
        h.setSource(ContentSource.CUSTOM);
        h.setSeverity(ThreatSeverity.SETBACK);
        h.setExposureMode(HazardExposureMode.ON_ENTER);
        return h;
    }

    @Test
    void createTrapReturns201WithLocationAndFiniteOrderedJson() throws Exception {
        UUID id = UUID.randomUUID();
        UUID conditionId = UUID.randomUUID();
        Trap t = trap(id, "Spike Pit");
        t.setDamageExpression("2d10");
        t.getDamageTypes().add(DamageType.PIERCING);
        t.getDamageTypes().add(DamageType.POISON);

        TrapDisarmMethod method = new TrapDisarmMethod();
        method.setId(UUID.randomUUID());
        method.setTrap(t);
        method.setMethodKey("jam-gears");
        method.setLabel("Jam the gears");
        method.setSortOrder(0);
        t.getDisarmMethods().add(method);

        ThreatReference ref = new ThreatReference();
        ref.setId(UUID.randomUUID());
        ref.setTrap(t);
        ref.setRole(ThreatReferenceRole.CONDITION);
        ref.setTargetType(CampaignContentType.CONDITION);
        ref.setTargetId(conditionId);
        ref.setDisplayText("Poisoned");
        ref.setSortOrder(0);
        t.getReferences().add(ref);

        when(trapService.create(any(), any(), any())).thenReturn(t);

        mockMvc.perform(post("/api/v1/traps")
                        .param("campaignId", campaignId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Spike Pit","sourceKey":"spike-pit","severity":"DANGEROUS",
                                "resetMode":"NONE","disarmMethods":[],"damageTypes":[],"references":[]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/library/traps/" + id))
                .andExpect(jsonPath("$.kind").value("TRAP"))
                .andExpect(jsonPath("$.disarmMethods[0].key").value("jam-gears"))
                .andExpect(jsonPath("$.references[0].targetType").value("CONDITION"))
                .andExpect(jsonPath("$.references[0].trap").doesNotExist())
                .andExpect(jsonPath("$.damageTypes[0]").value("PIERCING"))
                .andExpect(jsonPath("$.damageTypes[1]").value("POISON"));
    }

    @Test
    void mappedTrapResponseSerializesWithoutPersistenceBackLinks() throws Exception {
        Trap t = trap(UUID.randomUUID(), "Serialization Trap");
        TrapDisarmMethod method = new TrapDisarmMethod();
        method.setTrap(t);
        method.setMethodKey("cut-wire");
        method.setLabel("Cut wire");
        t.getDisarmMethods().add(method);
        ThreatReference ref = new ThreatReference();
        ref.setTrap(t);
        ref.setRole(ThreatReferenceRole.CONDITION);
        ref.setTargetType(CampaignContentType.CONDITION);
        ref.setTargetId(UUID.randomUUID());
        t.getReferences().add(ref);

        String json = JsonMapper.builder().build()
                .writeValueAsString(ThreatWebMapper.fromTrap(t));

        assertThat(json)
                .contains("\"key\":\"cut-wire\"")
                .contains("\"targetType\":\"CONDITION\"")
                .doesNotContain("\"trap\"")
                .doesNotContain("\"hazard\"");
    }

    @Test
    void createTrapReturns400WithProblemDetailPaths() throws Exception {
        when(trapService.create(any(), any(), any()))
                .thenThrow(new ThreatValidationException(List.of(
                        new ThreatValidationProblem("THREAT_NAME_REQUIRED", "/name", "Name is required"))));

        mockMvc.perform(post("/api/v1/traps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"sourceKey\":\"x\",\"severity\":\"SETBACK\",\"resetMode\":\"NONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems").isArray())
                .andExpect(jsonPath("$.problems[0].code").value("THREAT_NAME_REQUIRED"))
                .andExpect(jsonPath("$.problems[0].path").value("/name"));
    }

    @Test
    void updateTrapReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(trapService.updateCustom(any(), any(), any())).thenReturn(trap(id, "Updated Trap"));

        mockMvc.perform(put("/api/v1/traps/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Trap","sourceKey":"updated-trap","severity":"DANGEROUS",
                                "resetMode":"NONE","disarmMethods":[],"damageTypes":[],"references":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Trap"))
                .andExpect(jsonPath("$.kind").value("TRAP"));
    }

    @Test
    void cloneTrapReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(trapService.cloneAsCustom(any(), any(), any())).thenReturn(trap(id, "Cloned Trap"));

        mockMvc.perform(post("/api/v1/traps/{id}/clone", UUID.randomUUID())
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cloned Trap"));
    }

    @Test
    void promoteTrapReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(trapService.promoteToGlobal(id)).thenReturn(trap(id, "Promoted Trap"));

        mockMvc.perform(post("/api/v1/traps/{id}/promote", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Promoted Trap"));
    }

    @Test
    void deleteTrapReturns204WhenConfirmed() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/traps/{id}", id)
                        .param("confirmed", "true"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTrapReturns409WhenNotConfirmedAndHasDependents() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Trap has 1 dependent(s); set confirmed=true to proceed"))
                .when(trapService).deleteCustom(id, false);

        mockMvc.perform(delete("/api/v1/traps/{id}", id)
                        .param("confirmed", "false"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Trap has 1 dependent(s); set confirmed=true to proceed"))
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void deletionImpactReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(trapService.deletionImpact(id))
                .thenReturn(new ThreatDeletionImpact(ThreatKind.TRAP, id, List.of()));

        mockMvc.perform(get("/api/v1/traps/{id}/deletion-impact", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threatKind").value("TRAP"))
                .andExpect(jsonPath("$.dependencies").isArray());
    }

    @Test
    void createHazardReturns201WithFiniteJson() throws Exception {
        UUID id = UUID.randomUUID();
        Hazard h = hazard(id, "Toxic Fog");
        ThreatCheck check = new ThreatCheck();
        check.setMode(ThreatCheckMode.SAVE);
        check.setAbility("CON");
        check.setDc(14);
        h.setCheck(check);
        h.setDamageExpression("1d6");
        h.getDamageTypes().add(DamageType.POISON);
        when(hazardService.create(any(), any(), any())).thenReturn(h);

        mockMvc.perform(post("/api/v1/hazards")
                        .param("campaignId", campaignId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Toxic Fog","sourceKey":"toxic-fog","severity":"SETBACK",
                                "exposureMode":"ON_ENTER","damageTypes":[],"references":[]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/library/hazards/" + id))
                .andExpect(jsonPath("$.kind").value("HAZARD"))
                .andExpect(jsonPath("$.exposureMode").value("ON_ENTER"))
                .andExpect(jsonPath("$.check.mode").value("SAVE"))
                .andExpect(jsonPath("$.references").isArray());
    }

    @Test
    void updateHazardReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(hazardService.updateCustom(any(), any(), any())).thenReturn(hazard(id, "Updated Hazard"));

        mockMvc.perform(put("/api/v1/hazards/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Hazard","sourceKey":"updated-hazard","severity":"SETBACK",
                                "exposureMode":"PER_ROUND","damageTypes":[],"references":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Hazard"))
                .andExpect(jsonPath("$.kind").value("HAZARD"));
    }

    @Test
    void clonePromoteDeleteHazardLifecycle() throws Exception {
        UUID id = UUID.randomUUID();
        when(hazardService.cloneAsCustom(any(), any(), any())).thenReturn(hazard(id, "Cloned Hazard"));
        when(hazardService.promoteToGlobal(id)).thenReturn(hazard(id, "Promoted Hazard"));

        mockMvc.perform(post("/api/v1/hazards/{id}/clone", UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cloned Hazard"));

        mockMvc.perform(post("/api/v1/hazards/{id}/promote", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Promoted Hazard"));

        mockMvc.perform(delete("/api/v1/hazards/{id}", id).param("confirmed", "true"))
                .andExpect(status().isNoContent());
    }

    @Test
    void createHazardReturns400WithProblems() throws Exception {
        when(hazardService.create(any(), any(), any()))
                .thenThrow(new ThreatValidationException(List.of(
                        new ThreatValidationProblem("HAZARD_EXPOSURE_REQUIRED", "/exposureMode",
                                "Exposure mode is required"))));

        mockMvc.perform(post("/api/v1/hazards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"sourceKey\":\"bad\",\"severity\":\"SETBACK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems[0].path").value("/exposureMode"));
    }

    @Test
    void referenceOptionsExcludeForeignAndExposeSafeDestination() throws Exception {
        var visible = new dev.hendrikhoemberg.dmhelper.library.data.Condition();
        visible.setId(UUID.randomUUID());
        visible.setName("Poisoned");
        when(conditionRepository.findByNameContainingIgnoreCaseOrderByNameAsc("poi"))
                .thenReturn(List.of(visible));
        when(referenceResolver.isVisibleToScope(
                eq(CampaignContentType.CONDITION), eq(visible.getId()), eq(campaignId)))
                .thenReturn(true);

        mockMvc.perform(get("/api/v1/traps/reference-options")
                        .param("campaignId", campaignId.toString())
                        .param("type", "CONDITION")
                        .param("q", "poi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Poisoned"))
                .andExpect(jsonPath("$[0].url").value("/library/conditions/" + visible.getId()));
    }

    @Test
    void referenceOptionsAvailableOnHazardsPath() throws Exception {
        mockMvc.perform(get("/api/v1/hazards/reference-options")
                        .param("type", "STATBLOCK")
                        .param("q", "gob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void threatCardViewIsFiniteUnionWithoutBackLinks() throws Exception {
        Trap t = trap(UUID.randomUUID(), "Card Trap");
        ThreatCardView card = ThreatWebMapper.cardFromTrap(t, "<p>safe</p>");
        String json = JsonMapper.builder().build().writeValueAsString(card);

        assertThat(json)
                .contains("\"kind\":\"TRAP\"")
                .contains("\"descriptionHtml\":\"<p>safe</p>\"")
                .contains("\"name\":\"Card Trap\"")
                .doesNotContain("\"disarmMethods\":[{\"trap\"");
        assertThat(card.trap()).isNotNull();
        assertThat(card.hazard()).isNull();
    }
}
