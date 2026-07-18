package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.ConditionService;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.EquipmentItemService;
import dev.hendrikhoemberg.dmhelper.library.service.LibraryReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.library.service.MagicItemService;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReference;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReferenceRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReferenceRole;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({LibraryReferenceCleaner.class, ConditionService.class, EquipmentItemService.class,
        MagicItemService.class, StatBlockService.class, SceneRefCleaner.class, CustomContentSupport.class,
        TrapService.class, HazardService.class, ThreatValidator.class, ThreatReferenceResolver.class,
        ThreatDependencyService.class})
class ThreatLibraryDependencyTest {

    @MockitoBean
    private CampaignPackageKeyService packageKeyService;

    @Autowired private LibraryReferenceCleaner cleaner;
    @Autowired private ConditionService conditionService;
    @Autowired private EquipmentItemService equipmentItemService;
    @Autowired private MagicItemService magicItemService;
    @Autowired private StatBlockService statBlockService;
    @Autowired private ConditionRepository conditionRepository;
    @Autowired private EquipmentItemRepository equipmentItemRepository;
    @Autowired private MagicItemRepository magicItemRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private TrapRepository trapRepository;
    @Autowired private ThreatReferenceRepository threatReferenceRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        threatReferenceRepository.deleteAll();
        trapRepository.deleteAll();
        conditionRepository.deleteAll();
        equipmentItemRepository.deleteAll();
        magicItemRepository.deleteAll();
        statBlockRepository.deleteAll();
        campaignRepository.deleteAll();

        campaign = new Campaign();
        campaign.setName("Lib Dep Campaign");
        campaign = campaignRepository.save(campaign);
    }

    @Test
    void conditionDeletionRejectedWhenReferencedByTrap() {
        Condition condition = new Condition();
        condition.setSource(ContentSource.CUSTOM);
        condition.setCampaign(campaign);
        condition.setSourceKey("poisoned-custom");
        condition.setName("Poisoned Custom");
        condition.setDescription("x");
        condition = conditionRepository.save(condition);

        Trap trap = minimalTrap();
        ThreatReference ref = new ThreatReference();
        ref.setTrap(trap);
        ref.setRole(ThreatReferenceRole.CONDITION);
        ref.setTargetType(CampaignContentType.CONDITION);
        ref.setTargetId(condition.getId());
        ref.setDisplayText("Poisoned");
        ref.setSortOrder(0);
        trap.getReferences().add(ref);
        trapRepository.save(trap);
        em.flush();

        assertThat(cleaner.countThreatReferences(CampaignContentType.CONDITION, condition.getId()))
                .isEqualTo(1);

        UUID conditionId = condition.getId();
        assertThatThrownBy(() -> conditionService.deleteCustom(conditionId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referenced");
    }

    @Test
    void equipmentAndMagicItemDeletionRejectedWhenReferencedAsSalvage() {
        EquipmentItem equipment = new EquipmentItem();
        equipment.setSource(ContentSource.CUSTOM);
        equipment.setCampaign(campaign);
        equipment.setSourceKey("vial");
        equipment.setName("Poison Vial");
        equipment.setCategory(EquipmentItem.Category.GEAR);
        equipment = equipmentItemRepository.save(equipment);

        MagicItem magicItem = new MagicItem();
        magicItem.setSource(ContentSource.CUSTOM);
        magicItem.setCampaign(campaign);
        magicItem.setSourceKey("rune");
        magicItem.setName("Trap Rune");
        magicItem = magicItemRepository.save(magicItem);

        Trap trap = minimalTrap();
        ThreatReference equipRef = new ThreatReference();
        equipRef.setTrap(trap);
        equipRef.setRole(ThreatReferenceRole.SALVAGE_ITEM);
        equipRef.setTargetType(CampaignContentType.EQUIPMENT_ITEM);
        equipRef.setTargetId(equipment.getId());
        equipRef.setDisplayText("Vial");
        equipRef.setSortOrder(0);
        trap.getReferences().add(equipRef);

        ThreatReference magicRef = new ThreatReference();
        magicRef.setTrap(trap);
        magicRef.setRole(ThreatReferenceRole.SALVAGE_ITEM);
        magicRef.setTargetType(CampaignContentType.MAGIC_ITEM);
        magicRef.setTargetId(magicItem.getId());
        magicRef.setDisplayText("Rune");
        magicRef.setSortOrder(1);
        trap.getReferences().add(magicRef);
        trapRepository.save(trap);
        em.flush();

        UUID equipId = equipment.getId();
        UUID magicId = magicItem.getId();
        assertThatThrownBy(() -> equipmentItemService.deleteCustom(equipId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referenced");
        assertThatThrownBy(() -> magicItemService.deleteCustom(magicId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referenced");
    }

    @Test
    void statBlockDeletionRejectedWhenLinkedByTrap() {
        StatBlock sb = new StatBlock();
        sb.setSource(ContentSource.CUSTOM);
        sb.setCampaign(campaign);
        sb.setSourceKey("anim-trap");
        sb.setName("Animated Trap");
        sb.setCr("1");
        sb.setType("construct");
        sb.setAc(12);
        sb.setHp("10");
        sb.setSpeed("0 ft.");
        sb = statBlockRepository.save(sb);

        Trap trap = minimalTrap();
        trap.setStatBlock(sb);
        trapRepository.save(trap);
        em.flush();

        assertThat(cleaner.countTrapStatBlockReferences(sb.getId())).isEqualTo(1);

        UUID sbId = sb.getId();
        assertThatThrownBy(() -> statBlockService.delete(sbId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trap");
    }

    @Test
    void unreferencedCustomLibraryContentCanStillBeDeleted() {
        Condition condition = new Condition();
        condition.setSource(ContentSource.CUSTOM);
        condition.setCampaign(campaign);
        condition.setSourceKey("lonely");
        condition.setName("Lonely");
        condition.setDescription("x");
        condition = conditionRepository.save(condition);
        em.flush();

        UUID id = condition.getId();
        assertThatCode(() -> conditionService.deleteCustom(id)).doesNotThrowAnyException();
        assertThat(conditionRepository.findById(id)).isEmpty();
    }

    private Trap minimalTrap() {
        Trap trap = new Trap();
        trap.setSourceKey("lib-dep-trap-" + System.nanoTime());
        trap.setSource(ContentSource.CUSTOM);
        trap.setCampaign(campaign);
        trap.setName("Lib Dep Trap");
        trap.setDescription("desc");
        trap.setSeverity(ThreatSeverity.SETBACK);
        trap.setResetMode(ThreatResetMode.NONE);
        return trap;
    }
}
