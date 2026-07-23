package dev.hendrikhoemberg.dmhelper.threat;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntry;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.CombatDifficultyCalculator;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.EncounterDto;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.ThreatCombatantRequest;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.live.TablePresentationService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatReferenceResolver;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        EncounterService.class,
        CombatDifficultyCalculator.class,
        GameMapService.class,
        DiceEngine.class,
        SceneRefCleaner.class,
        SessionReferenceCleaner.class,
        ThreatReferenceResolver.class,
        MarkdownUtil.class,
        ThreatEncounterIntegrationTest.MockConfig.class
})
class ThreatEncounterIntegrationTest {

    @TestConfiguration
    static class MockConfig {
        @Bean
        TablePresentationService tablePresentationService() {
            return Mockito.mock(TablePresentationService.class);
        }
    }

    @MockitoBean
    private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @Autowired private EncounterService encounterService;
    @Autowired private TrapRepository trapRepository;
    @Autowired private HazardRepository hazardRepository;
    @Autowired private CombatantRepository combatantRepository;
    @Autowired private CombatLogEntryRepository combatLogRepo;
    @Autowired private EntityManager em;

    private Campaign campaign;
    private Campaign otherCampaign;
    private Trap campaignTrap;
    private Trap otherTrap;
    private Hazard campaignHazard;
    private UUID encounterId;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Threat Encounter Campaign");
        em.persist(campaign);

        otherCampaign = new Campaign();
        otherCampaign.setName("Other Campaign");
        em.persist(otherCampaign);

        campaignTrap = newTrap(campaign, "camp-spike", "Campaign Spike Trap");
        otherTrap = newTrap(otherCampaign, "other-spike", "Foreign Spike");
        campaignHazard = newHazard(campaign, "camp-gas", "Campaign Poison Gas");
        em.flush();

        EncounterDto enc = encounterService.create(campaign.getId(),
                new CreateRequest("Trap Corridor", null));
        encounterId = enc.id();
    }

    @Test
    void addsVisibleTrapAndHazardAsNonCreatureCombatants() {
        CombatantDto trap = encounterService.addThreatCombatant(encounterId,
                new ThreatCombatantRequest(ThreatKind.TRAP, campaignTrap.getId(), null, 15, null));
        CombatantDto hazard = encounterService.addThreatCombatant(encounterId,
                new ThreatCombatantRequest(ThreatKind.HAZARD, campaignHazard.getId(), "Custom Gas Name", 5, null));

        assertThat(trap.kind()).isEqualTo("TRAP");
        assertThat(trap.threatKind()).isEqualTo(ThreatKind.TRAP);
        assertThat(trap.threatId()).isEqualTo(campaignTrap.getId());
        assertThat(trap.name()).isEqualTo("Campaign Spike Trap");
        assertThat(trap.maxHp()).isZero();
        assertThat(trap.currentHp()).isZero();
        assertThat(trap.statBlockId()).isNull();
        assertThat(trap.partyMemberId()).isNull();
        assertThat(trap.threatCard()).isNotNull();
        assertThat(trap.threatCard().kind()).isEqualTo(ThreatKind.TRAP);
        assertThat(trap.threatCard().trap()).isNotNull();
        assertThat(trap.threatCard().hazard()).isNull();
        assertThat(trap.initiative()).isEqualTo(15);

        assertThat(hazard.kind()).isEqualTo("HAZARD");
        assertThat(hazard.threatKind()).isEqualTo(ThreatKind.HAZARD);
        assertThat(hazard.threatId()).isEqualTo(campaignHazard.getId());
        assertThat(hazard.name()).isEqualTo("Custom Gas Name");
        assertThat(hazard.maxHp()).isZero();
        assertThat(hazard.threatCard()).isNotNull();
        assertThat(hazard.threatCard().hazard()).isNotNull();
        assertThat(hazard.threatCard().trap()).isNull();
    }

    @Test
    void rejectsCrossCampaignAndKindMismatch() {
        assertThatThrownBy(() -> encounterService.addThreatCombatant(encounterId,
                new ThreatCombatantRequest(ThreatKind.TRAP, otherTrap.getId(), null, null, null)))
                .isInstanceOf(NotFoundException.class);

        assertThatThrownBy(() -> encounterService.addThreatCombatant(encounterId,
                new ThreatCombatantRequest(ThreatKind.TRAP, campaignHazard.getId(), null, null, null)))
                .isInstanceOf(NotFoundException.class);

        assertThatThrownBy(() -> encounterService.addThreatCombatant(encounterId,
                new ThreatCombatantRequest(ThreatKind.HAZARD, campaignTrap.getId(), null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void preservesThreatRefOverReorderActivationAndRefresh() {
        CombatantDto trap = encounterService.addThreatCombatant(encounterId,
                new ThreatCombatantRequest(ThreatKind.TRAP, campaignTrap.getId(), null, 20, null));
        CombatantDto goblin = encounterService.addCombatant(encounterId,
                new CombatantCreateRequest("Goblin", 7, "MONSTER", null, null, null));
        encounterService.setInitiative(goblin.id(), 10);

        encounterService.reorderCombatants(encounterId, List.of(goblin.id(), trap.id()));
        CombatantDto afterReorder = encounterService.getCombatant(trap.id());
        assertThat(afterReorder.threatKind()).isEqualTo(ThreatKind.TRAP);
        assertThat(afterReorder.threatId()).isEqualTo(campaignTrap.getId());
        assertThat(afterReorder.kind()).isEqualTo("TRAP");

        encounterService.activate(encounterId);
        CombatantDto afterActivate = encounterService.getCombatant(trap.id());
        assertThat(afterActivate.threatKind()).isEqualTo(ThreatKind.TRAP);
        assertThat(afterActivate.threatId()).isEqualTo(campaignTrap.getId());

        em.flush();
        em.clear();

        List<CombatantDto> refreshed = encounterService.getCombatants(encounterId);
        CombatantDto reloaded = refreshed.stream()
                .filter(c -> c.id().equals(trap.id()))
                .findFirst()
                .orElseThrow();
        assertThat(reloaded.threatKind()).isEqualTo(ThreatKind.TRAP);
        assertThat(reloaded.threatId()).isEqualTo(campaignTrap.getId());
        assertThat(reloaded.threatCard()).isNotNull();
        assertThat(reloaded.maxHp()).isZero();

        Combatant entity = combatantRepository.findById(trap.id()).orElseThrow();
        assertThat(entity.getThreatKind()).isEqualTo(ThreatKind.TRAP);
        assertThat(entity.getThreatId()).isEqualTo(campaignTrap.getId());
        assertThat(entity.getStatBlock()).isNull();
        assertThat(entity.getPartyMember()).isNull();
    }

    @Test
    void threatCardPresentOnDtoButActivationDoesNotMutateDamageOrConditions() {
        CombatantDto trap = encounterService.addThreatCombatant(encounterId,
                new ThreatCombatantRequest(ThreatKind.TRAP, campaignTrap.getId(), null, 18, null));
        CombatantDto fighter = encounterService.addCombatant(encounterId,
                new CombatantCreateRequest("Fighter", 30, "PC", null, null, null));
        encounterService.setInitiative(fighter.id(), 12);

        encounterService.activate(encounterId);
        encounterService.startCombat(encounterId, false);

        long logAfterActivate = combatLogRepo.findByEncounterIdOrderBySequenceAsc(encounterId).stream()
                .filter(e -> e.getType() == CombatLogEntry.EntryType.DAMAGE
                        || e.getType() == CombatLogEntry.EntryType.CONDITION_ADDED)
                .count();
        assertThat(logAfterActivate).isZero();

        encounterService.setActiveTurn(encounterId, trap.id());
        CombatantDto activeTrap = encounterService.getCombatant(trap.id());
        assertThat(activeTrap.threatCard()).isNotNull();
        assertThat(activeTrap.threatCard().id()).isEqualTo(campaignTrap.getId());

        // Non-active combatants still carry the finite card payload for clients,
        // but UI contracts render only for the active threat turn.
        List<CombatantDto> all = encounterService.getCombatants(encounterId);
        assertThat(all).filteredOn(c -> c.threatId() != null).allMatch(c -> c.threatCard() != null);

        long logAfterTurn = combatLogRepo.findByEncounterIdOrderBySequenceAsc(encounterId).stream()
                .filter(e -> e.getType() == CombatLogEntry.EntryType.DAMAGE
                        || e.getType() == CombatLogEntry.EntryType.CONDITION_ADDED)
                .count();
        assertThat(logAfterTurn).isZero();
    }

    @Test
    void manualDamageAndConditionLoggingIsRequiredProofPath() {
        CombatantDto trap = encounterService.addThreatCombatant(encounterId,
                new ThreatCombatantRequest(ThreatKind.TRAP, campaignTrap.getId(), null, 20, null));
        CombatantDto goblin = encounterService.addCombatant(encounterId,
                new CombatantCreateRequest("Goblin", 15, "MONSTER", null, null, null));
        encounterService.setInitiative(goblin.id(), 8);

        encounterService.activate(encounterId);
        encounterService.startCombat(encounterId, false);
        encounterService.setActiveTurn(encounterId, trap.id());

        List<CombatLogEntry> beforeManual = combatLogRepo.findByEncounterIdOrderBySequenceAsc(encounterId);
        assertThat(beforeManual).noneMatch(e ->
                e.getType() == CombatLogEntry.EntryType.DAMAGE
                        || e.getType() == CombatLogEntry.EntryType.CONDITION_ADDED);

        encounterService.applyDamage(goblin.id(), -5);
        encounterService.toggleCondition(goblin.id(), "poisoned", 2);

        List<CombatLogEntry> afterManual = combatLogRepo.findByEncounterIdOrderBySequenceAsc(encounterId);
        assertThat(afterManual).anyMatch(e -> e.getType() == CombatLogEntry.EntryType.DAMAGE);
        assertThat(afterManual).anyMatch(e -> e.getType() == CombatLogEntry.EntryType.CONDITION_ADDED);

        CombatantDto damaged = encounterService.getCombatant(goblin.id());
        assertThat(damaged.currentHp()).isEqualTo(10);
        assertThat(damaged.conditions()).extracting(EncounterService.ConditionStateDto::sourceKey)
                .contains("poisoned");
    }

    @Test
    void updateCannotChangeThreatCombatantToMismatchedKind() {
        CombatantDto trap = encounterService.addThreatCombatant(encounterId,
                new ThreatCombatantRequest(ThreatKind.TRAP, campaignTrap.getId(), null, null, null));

        assertThatThrownBy(() -> encounterService.updateCombatant(trap.id(),
                new EncounterService.CombatantUpdateRequest(
                        null, null, null, null, null, null,
                        "HAZARD", null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kind");
    }

    private Trap newTrap(Campaign owner, String key, String name) {
        Trap trap = new Trap();
        trap.setSourceKey(key);
        trap.setSource(ContentSource.CUSTOM);
        trap.setCampaign(owner);
        trap.setName(name);
        trap.setDescription("Spike trap description long enough.");
        trap.setSeverity(ThreatSeverity.DANGEROUS);
        trap.setResetMode(ThreatResetMode.NONE);
        trap.setDamageExpression("2d10");
        trap.getDamageTypes().add(DamageType.PIERCING);
        return trapRepository.save(trap);
    }

    private Hazard newHazard(Campaign owner, String key, String name) {
        Hazard hazard = new Hazard();
        hazard.setSourceKey(key);
        hazard.setSource(ContentSource.CUSTOM);
        hazard.setCampaign(owner);
        hazard.setName(name);
        hazard.setDescription("Poison gas description long enough.");
        hazard.setSeverity(ThreatSeverity.SETBACK);
        hazard.setExposureMode(HazardExposureMode.ON_ENTER);
        hazard.setDamageExpression("1d6");
        hazard.getDamageTypes().add(DamageType.POISON);
        return hazardRepository.save(hazard);
    }
}
