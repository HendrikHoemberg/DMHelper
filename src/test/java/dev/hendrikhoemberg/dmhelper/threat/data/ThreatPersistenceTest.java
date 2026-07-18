package dev.hendrikhoemberg.dmhelper.threat.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.LicenseClassification;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ThreatPersistenceTest {

    @Autowired private EntityManager em;
    @Autowired private TrapRepository trapRepository;
    @Autowired private HazardRepository hazardRepository;
    @Autowired private MapThreatPinRepository mapThreatPinRepository;
    @Autowired private ThreatReferenceRepository threatReferenceRepository;

    private Campaign campaign;
    private GameMap gameMap;
    private StatBlock statBlock;
    private UUID conditionTargetId;
    private UUID itemTargetId;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Threat Persistence Campaign");
        em.persist(campaign);

        gameMap = new GameMap();
        gameMap.setCampaign(campaign);
        gameMap.setName("Crypt Map");
        gameMap.setSortOrder(0);
        em.persist(gameMap);

        statBlock = new StatBlock();
        statBlock.setSource(ContentSource.CUSTOM);
        statBlock.setCampaign(campaign);
        statBlock.setName("Animated Trap");
        statBlock.setCr("1");
        statBlock.setType("construct");
        statBlock.setAc(12);
        statBlock.setHp("22 (4d8+4)");
        em.persist(statBlock);

        conditionTargetId = UUID.randomUUID();
        itemTargetId = UUID.randomUUID();
        em.flush();
    }

    @Test
    void persistsAndReloadsCompleteTrapGraph() {
        ContentProvenance provenance = new ContentProvenance();
        provenance.setSourceTitle("Dungeon Master's Guide");
        provenance.setLicenseClassification(LicenseClassification.ORIGINAL);

        Trap trap = new Trap();
        trap.setSourceKey("poisoned-needle");
        trap.setSource(ContentSource.CUSTOM);
        trap.setCampaign(campaign);
        trap.setProvenance(provenance);
        trap.setName("Poisoned Needle");
        trap.setDescription("A needle hidden in a lock.");
        trap.setSeverity(ThreatSeverity.DANGEROUS);
        trap.setMinLevel(3);
        trap.setMaxLevel(7);
        trap.setTriggerDescription("Opening the lock without the key");
        trap.setTriggerAreaHint("lock plate");
        trap.setDetectionPassiveThreshold(15);

        ThreatCheck detection = new ThreatCheck();
        detection.setMode(ThreatCheckMode.CHECK);
        detection.setAbility("WIS");
        detection.setSkill("Perception");
        detection.setDc(15);
        trap.setDetectionCheck(detection);

        trap.setAttackBonus(null);
        ThreatCheck save = new ThreatCheck();
        save.setMode(ThreatCheckMode.SAVE);
        save.setAbility("CON");
        save.setDc(13);
        trap.setSave(save);

        trap.setDamageExpression("1d10");
        trap.getDamageTypes().add(DamageType.PIERCING);
        trap.getDamageTypes().add(DamageType.POISON);
        trap.setAdditionalEffect("Target is poisoned for 1 hour on a failed save.");
        trap.setResetMode(ThreatResetMode.MANUAL);
        trap.setResetTiming(null);
        trap.setStatBlock(statBlock);
        trap.setCountermeasureNotes("Gloves or mage hand avoid contact.");

        TrapDisarmMethod jam = new TrapDisarmMethod();
        jam.setTrap(trap);
        jam.setMethodKey("jam-gears");
        jam.setLabel("Jam the gears");
        jam.setAbility("DEX");
        jam.setSkill("Sleight of Hand");
        jam.setDc(14);
        jam.setFailureConsequence("Needle fires early");
        jam.setSortOrder(0);
        trap.getDisarmMethods().add(jam);

        TrapDisarmMethod arcana = new TrapDisarmMethod();
        arcana.setTrap(trap);
        arcana.setMethodKey("arcana-bypass");
        arcana.setLabel("Arcane bypass");
        arcana.setAbility("INT");
        arcana.setSkill("Arcana");
        arcana.setTool(null);
        arcana.setDc(16);
        arcana.setFailureConsequence("Alarm sounds");
        arcana.setSortOrder(1);
        trap.getDisarmMethods().add(arcana);

        ThreatReference conditionRef = new ThreatReference();
        conditionRef.setTrap(trap);
        conditionRef.setRole(ThreatReferenceRole.CONDITION);
        conditionRef.setTargetType(CampaignContentType.CONDITION);
        conditionRef.setTargetId(conditionTargetId);
        conditionRef.setDisplayText("Poisoned");
        conditionRef.setSortOrder(0);
        trap.getReferences().add(conditionRef);

        ThreatReference salvageRef = new ThreatReference();
        salvageRef.setTrap(trap);
        salvageRef.setRole(ThreatReferenceRole.SALVAGE_ITEM);
        salvageRef.setTargetType(CampaignContentType.EQUIPMENT_ITEM);
        salvageRef.setTargetId(itemTargetId);
        salvageRef.setDisplayText("Poison vial");
        salvageRef.setSortOrder(1);
        trap.getReferences().add(salvageRef);

        Trap saved = trapRepository.saveAndFlush(trap);
        em.clear();

        Trap loaded = trapRepository.findDetailedById(saved.getId()).orElseThrow();
        assertThat(loaded.getSourceKey()).isEqualTo("poisoned-needle");
        assertThat(loaded.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(loaded.getCampaign().getId()).isEqualTo(campaign.getId());
        assertThat(loaded.getProvenance().getSourceTitle()).isEqualTo("Dungeon Master's Guide");
        assertThat(loaded.getProvenance().getLicenseClassification()).isEqualTo(LicenseClassification.ORIGINAL);
        assertThat(loaded.getName()).isEqualTo("Poisoned Needle");
        assertThat(loaded.getDescription()).isEqualTo("A needle hidden in a lock.");
        assertThat(loaded.getSeverity()).isEqualTo(ThreatSeverity.DANGEROUS);
        assertThat(loaded.getMinLevel()).isEqualTo(3);
        assertThat(loaded.getMaxLevel()).isEqualTo(7);
        assertThat(loaded.getTriggerDescription()).isEqualTo("Opening the lock without the key");
        assertThat(loaded.getTriggerAreaHint()).isEqualTo("lock plate");
        assertThat(loaded.getDetectionPassiveThreshold()).isEqualTo(15);
        assertThat(loaded.getDetectionCheck().getMode()).isEqualTo(ThreatCheckMode.CHECK);
        assertThat(loaded.getDetectionCheck().getAbility()).isEqualTo("WIS");
        assertThat(loaded.getDetectionCheck().getSkill()).isEqualTo("Perception");
        assertThat(loaded.getDetectionCheck().getDc()).isEqualTo(15);
        assertThat(loaded.getAttackBonus()).isNull();
        assertThat(loaded.getSave().getMode()).isEqualTo(ThreatCheckMode.SAVE);
        assertThat(loaded.getSave().getAbility()).isEqualTo("CON");
        assertThat(loaded.getSave().getDc()).isEqualTo(13);
        assertThat(loaded.getDamageExpression()).isEqualTo("1d10");
        assertThat(loaded.getDamageTypes())
                .containsExactly(DamageType.PIERCING, DamageType.POISON);
        assertThat(loaded.getAdditionalEffect()).contains("poisoned for 1 hour");
        assertThat(loaded.getResetMode()).isEqualTo(ThreatResetMode.MANUAL);
        assertThat(loaded.getStatBlock().getId()).isEqualTo(statBlock.getId());
        assertThat(loaded.getCountermeasureNotes()).contains("Gloves");
        assertThat(loaded.getCreatedAt()).isNotNull();

        assertThat(loaded.getDisarmMethods())
                .extracting(TrapDisarmMethod::getMethodKey)
                .containsExactly("jam-gears", "arcana-bypass");
        assertThat(loaded.getDisarmMethods().get(0).getLabel()).isEqualTo("Jam the gears");
        assertThat(loaded.getDisarmMethods().get(0).getSkill()).isEqualTo("Sleight of Hand");
        assertThat(loaded.getDisarmMethods().get(1).getAbility()).isEqualTo("INT");

        assertThat(loaded.getReferences())
                .extracting(ThreatReference::getRole)
                .containsExactly(ThreatReferenceRole.CONDITION, ThreatReferenceRole.SALVAGE_ITEM);
        assertThat(loaded.getReferences().get(0).getTargetId()).isEqualTo(conditionTargetId);
        assertThat(loaded.getReferences().get(1).getTargetType()).isEqualTo(CampaignContentType.EQUIPMENT_ITEM);
        assertThat(loaded.getReferences().get(1).getDisplayText()).isEqualTo("Poison vial");

        assertThat(trapRepository.countByStatBlockId(statBlock.getId())).isEqualTo(1);
        assertThat(threatReferenceRepository.findByTargetTypeAndTargetId(
                CampaignContentType.CONDITION, conditionTargetId)).hasSize(1);
    }

    @Test
    void persistsAndReloadsCompleteHazardGraph() {
        ContentProvenance provenance = new ContentProvenance();
        provenance.setSourceTitle("Homebrew Hazards");
        provenance.setLicenseClassification(LicenseClassification.ORIGINAL);

        Hazard hazard = new Hazard();
        hazard.setSourceKey("green-slime");
        hazard.setSource(ContentSource.CUSTOM);
        hazard.setCampaign(campaign);
        hazard.setProvenance(provenance);
        hazard.setName("Green Slime");
        hazard.setDescription("A patch of corrosive slime.");
        hazard.setSeverity(ThreatSeverity.SETBACK);
        hazard.setMinLevel(1);
        hazard.setMaxLevel(4);
        hazard.setExposureMode(HazardExposureMode.ON_ENTER);
        hazard.setExposureText("When a creature enters the area");
        hazard.setAreaHint("10-foot square");

        ThreatCheck check = new ThreatCheck();
        check.setMode(ThreatCheckMode.SAVE);
        check.setAbility("DEX");
        check.setDc(12);
        hazard.setCheck(check);

        hazard.setDamageExpression("1d6");
        hazard.getDamageTypes().add(DamageType.ACID);
        hazard.setEscalationText("Spreads 5 feet each hour");
        hazard.setEndingConditions("Destroyed by fire, cold, sunlight, or scrap");

        ThreatReference conditionRef = new ThreatReference();
        conditionRef.setHazard(hazard);
        conditionRef.setRole(ThreatReferenceRole.CONDITION);
        conditionRef.setTargetType(CampaignContentType.CONDITION);
        conditionRef.setTargetId(conditionTargetId);
        conditionRef.setDisplayText("Restrained by slime");
        conditionRef.setSortOrder(0);
        hazard.getReferences().add(conditionRef);

        Hazard saved = hazardRepository.saveAndFlush(hazard);
        em.clear();

        Hazard loaded = hazardRepository.findDetailedById(saved.getId()).orElseThrow();
        assertThat(loaded.getSourceKey()).isEqualTo("green-slime");
        assertThat(loaded.getCampaign().getId()).isEqualTo(campaign.getId());
        assertThat(loaded.getProvenance().getSourceTitle()).isEqualTo("Homebrew Hazards");
        assertThat(loaded.getName()).isEqualTo("Green Slime");
        assertThat(loaded.getSeverity()).isEqualTo(ThreatSeverity.SETBACK);
        assertThat(loaded.getExposureMode()).isEqualTo(HazardExposureMode.ON_ENTER);
        assertThat(loaded.getExposureText()).isEqualTo("When a creature enters the area");
        assertThat(loaded.getAreaHint()).isEqualTo("10-foot square");
        assertThat(loaded.getCheck().getMode()).isEqualTo(ThreatCheckMode.SAVE);
        assertThat(loaded.getCheck().getAbility()).isEqualTo("DEX");
        assertThat(loaded.getCheck().getDc()).isEqualTo(12);
        assertThat(loaded.getDamageExpression()).isEqualTo("1d6");
        assertThat(loaded.getDamageTypes()).containsExactly(DamageType.ACID);
        assertThat(loaded.getEscalationText()).contains("Spreads");
        assertThat(loaded.getEndingConditions()).contains("fire");
        assertThat(loaded.getReferences())
                .extracting(ThreatReference::getRole)
                .containsExactly(ThreatReferenceRole.CONDITION);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void persistsMapThreatPinCoordinates() {
        Trap trap = minimalTrap("pin-trap", "Pin Trap");
        trapRepository.saveAndFlush(trap);

        MapThreatPin pin = new MapThreatPin();
        pin.setMap(gameMap);
        pin.setPinKey("trap-pin-1");
        pin.setThreatKind(ThreatKind.TRAP);
        pin.setThreatId(trap.getId());
        pin.setXPx(120);
        pin.setYPx(240);
        pin.setLabel("Needle lock");
        pin.setSortOrder(0);
        mapThreatPinRepository.saveAndFlush(pin);
        em.clear();

        List<MapThreatPin> pins = mapThreatPinRepository.findByMapIdOrderBySortOrderAsc(gameMap.getId());
        assertThat(pins).hasSize(1);
        MapThreatPin loaded = pins.get(0);
        assertThat(loaded.getPinKey()).isEqualTo("trap-pin-1");
        assertThat(loaded.getThreatKind()).isEqualTo(ThreatKind.TRAP);
        assertThat(loaded.getThreatId()).isEqualTo(trap.getId());
        assertThat(loaded.getXPx()).isEqualTo(120);
        assertThat(loaded.getYPx()).isEqualTo(240);
        assertThat(loaded.getLabel()).isEqualTo("Needle lock");
        assertThat(mapThreatPinRepository.findByThreatKindAndThreatId(ThreatKind.TRAP, trap.getId()))
                .hasSize(1);
    }

    @Test
    void visibleQueryIncludesSameCampaignGlobalAndSrdButExcludesOtherCampaign() {
        Campaign other = new Campaign();
        other.setName("Other Campaign");
        em.persist(other);

        trapRepository.save(minimalTrap("campaign-trap", "Campaign Trap", ContentSource.CUSTOM, campaign));
        trapRepository.save(minimalTrap("global-trap", "Global Trap", ContentSource.CUSTOM, null));
        trapRepository.save(minimalTrap("srd-trap", "SRD Trap", ContentSource.SRD, null));
        trapRepository.save(minimalTrap("foreign-trap", "Foreign Trap", ContentSource.CUSTOM, other));
        em.flush();
        em.clear();

        assertThat(trapRepository.findVisibleByCampaignId(campaign.getId()))
                .extracting(Trap::getName)
                .containsExactly("Campaign Trap", "Global Trap", "SRD Trap")
                .doesNotContain("Foreign Trap");
        assertThat(trapRepository.findVisibleByCampaignId(null))
                .extracting(Trap::getName)
                .containsExactly("Global Trap", "SRD Trap")
                .doesNotContain("Campaign Trap", "Foreign Trap");

        hazardRepository.save(minimalHazard("campaign-hazard", "Campaign Hazard", ContentSource.CUSTOM, campaign));
        hazardRepository.save(minimalHazard("global-hazard", "Global Hazard", ContentSource.CUSTOM, null));
        hazardRepository.save(minimalHazard("srd-hazard", "SRD Hazard", ContentSource.SRD, null));
        hazardRepository.save(minimalHazard("foreign-hazard", "Foreign Hazard", ContentSource.CUSTOM, other));
        em.flush();
        em.clear();

        assertThat(hazardRepository.findVisibleByCampaignId(campaign.getId()))
                .extracting(Hazard::getName)
                .containsExactly("Campaign Hazard", "Global Hazard", "SRD Hazard")
                .doesNotContain("Foreign Hazard");
    }

    @Test
    void sourceKeyUniquenessHelpers() {
        trapRepository.saveAndFlush(minimalTrap("dup-key", "A", ContentSource.CUSTOM, null));
        assertThat(trapRepository.existsBySourceAndSourceKeyAndCampaignIsNull(
                ContentSource.CUSTOM, "dup-key")).isTrue();
        assertThat(trapRepository.existsByCampaignIdAndSourceKey(campaign.getId(), "dup-key")).isFalse();

        trapRepository.saveAndFlush(minimalTrap("camp-key", "B", ContentSource.CUSTOM, campaign));
        assertThat(trapRepository.existsByCampaignIdAndSourceKey(campaign.getId(), "camp-key")).isTrue();

        hazardRepository.saveAndFlush(minimalHazard("h-dup", "H", ContentSource.CUSTOM, null));
        assertThat(hazardRepository.existsBySourceAndSourceKeyAndCampaignIsNull(
                ContentSource.CUSTOM, "h-dup")).isTrue();
        assertThat(hazardRepository.existsByCampaignIdAndSourceKey(campaign.getId(), "h-dup")).isFalse();
    }

    private Trap minimalTrap(String key, String name) {
        return minimalTrap(key, name, ContentSource.CUSTOM, campaign);
    }

    private Trap minimalTrap(String key, String name, ContentSource source, Campaign owner) {
        Trap trap = new Trap();
        trap.setSourceKey(key);
        trap.setSource(source);
        trap.setCampaign(owner);
        trap.setName(name);
        trap.setDescription("");
        trap.setSeverity(ThreatSeverity.SETBACK);
        trap.setResetMode(ThreatResetMode.NONE);
        return trap;
    }

    private Hazard minimalHazard(String key, String name, ContentSource source, Campaign owner) {
        Hazard hazard = new Hazard();
        hazard.setSourceKey(key);
        hazard.setSource(source);
        hazard.setCampaign(owner);
        hazard.setName(name);
        hazard.setDescription("");
        hazard.setSeverity(ThreatSeverity.SETBACK);
        hazard.setExposureMode(HazardExposureMode.CONTINUOUS);
        return hazard;
    }
}
