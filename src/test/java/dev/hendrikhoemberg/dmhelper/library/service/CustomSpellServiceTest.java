package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReference;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({SpellService.class, CustomContentSupport.class, LibraryReferenceCleaner.class})
class CustomSpellServiceTest {

    @MockitoBean
    private CampaignPackageKeyService packageKeyService;

    @Autowired
    private SpellRepository repository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private SpellService service;

    @Autowired
    private PartyMemberRepository partyMemberRepository;

    @Autowired
    private CharacterSheetRepository sheetRepository;

    @Autowired
    private SheetSpellReferenceRepository sheetSpellRefRepository;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        sheetSpellRefRepository.deleteAll();
        sheetRepository.deleteAll();
        partyMemberRepository.deleteAll();
        campaignRepository.deleteAll();
        Campaign campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaign = campaignRepository.save(campaign);
        campaignId = campaign.getId();
    }

    private Spell createCustomSpell(String name) {
        SpellService.SpellWrite write = new SpellService.SpellWrite(
            name, 1, "Evocation", "1 action", "60 ft.",
            "V, S", "Instantaneous", "Deals fire damage.",
            null, false, false, null
        );
        return service.createCustom(campaignId, write, null);
    }

    @Test
    void shouldCreateCampaignSpell() {
        Spell spell = createCustomSpell("Test Fireball");
        assertThat(spell.getId()).isNotNull();
        assertThat(spell.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(spell.getCampaign().getId()).isEqualTo(campaignId);
        assertThat(spell.getName()).isEqualTo("Test Fireball");
        assertThat(spell.getSourceKey()).isEqualTo("test-fireball");
    }

    @Test
    void shouldRejectBlankName() {
        SpellService.SpellWrite write = new SpellService.SpellWrite(
            "", 1, "Evocation", null, null,
            null, null, null, null, false, false, null
        );
        assertThatThrownBy(() -> service.createCustom(campaignId, write, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPromoteClearsCampaignAndKeepsProvenance() {
        Spell spell = createCustomSpell("Promote Me");
        assertThat(spell.getCampaign()).isNotNull();

        Spell promoted = service.promoteToGlobal(spell.getId());
        assertThat(promoted.getCampaign()).isNull();
        assertThat(promoted.getSource()).isEqualTo(ContentSource.CUSTOM);
    }

    @Test
    void shouldCloneSrdAsCustom() {
        Spell srd = new Spell();
        srd.setSource(ContentSource.SRD);
        srd.setName("Fireball");
        srd.setSourceKey("fireball");
        srd.setLevel(3);
        srd.setSchool("Evocation");
        srd.setCastingTime("1 action");
        srd.setRange("150 ft.");
        srd.setComponents("V, S, M");
        srd.setDuration("Instantaneous");
        srd.setDescription("A bright streak flashes from you.");
        srd = repository.save(srd);

        Spell cloned = service.cloneAsCustom(srd.getId(), campaignId, "Custom Fireball");
        assertThat(cloned.getId()).isNotEqualTo(srd.getId());
        assertThat(cloned.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(cloned.getCampaign().getId()).isEqualTo(campaignId);
        assertThat(cloned.getName()).isEqualTo("Custom Fireball");
        assertThat(cloned.getSourceKey()).isEqualTo("custom-fireball");
        assertThat(cloned.getLevel()).isEqualTo(3);
    }

    @Test
    void shouldDeleteCustomSpell() {
        Spell spell = createCustomSpell("Delete Me");
        service.deleteCustom(spell.getId());
        assertThat(repository.findById(spell.getId())).isEmpty();
    }

    @Test
    void shouldNotDeleteSrdSpell() {
        Spell srd = new Spell();
        srd.setSource(ContentSource.SRD);
        srd.setName("Protected Spell");
        srd.setSourceKey("protected-spell");
        srd.setLevel(1);
        srd.setSchool("Abjuration");
        srd.setDescription("Protected");
        Spell saved = repository.save(srd);

        assertThatThrownBy(() -> service.deleteCustom(saved.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("custom content");
    }

    @Test
    void shouldNotMutateSrdSpell() {
        Spell srd = new Spell();
        srd.setSource(ContentSource.SRD);
        srd.setName("Immovable Spell");
        srd.setSourceKey("immovable-spell");
        srd.setLevel(1);
        srd.setSchool("Abjuration");
        srd.setDescription("Cannot be changed.");
        Spell saved = repository.save(srd);

        SpellService.SpellWrite write = new SpellService.SpellWrite(
            "Changed", 1, "Evocation", null, null,
            null, null, null, null, false, false, null
        );

        assertThatThrownBy(() -> service.updateCustom(saved.getId(), write, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("custom content");
    }

    @Test
    void shouldBlockDeleteWhenSheetReferencesSpell() {
        Spell spell = createCustomSpell("Sheet Referenced Spell");

        PartyMember member = new PartyMember();
        member.setCampaign(campaignRepository.findById(campaignId).orElseThrow());
        member.setCharacterName("Test Character");
        member.setAc(10);
        member.setMaxHp(10);
        member.setInitiativeBonus(0);
        member.setSpeed(30);
        member.setPassivePerception(10);
        member.setPassiveInsight(10);
        member.setPassiveInvestigation(10);
        member = partyMemberRepository.save(member);

        CharacterSheet sheet = new CharacterSheet();
        sheet.setPartyMember(member);
        sheet = sheetRepository.save(sheet);

        SheetSpellReference ref = new SheetSpellReference();
        ref.setSheet(sheet);
        ref.setSpell(spell);
        sheetSpellRefRepository.save(ref);

        assertThatThrownBy(() -> service.deleteCustom(spell.getId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("referenced");
    }
}
