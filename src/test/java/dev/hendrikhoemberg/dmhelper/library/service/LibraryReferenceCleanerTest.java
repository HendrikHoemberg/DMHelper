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

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({LibraryReferenceCleaner.class})
class LibraryReferenceCleanerTest {

    @MockitoBean
    private CampaignPackageKeyService packageKeyService;

    @Autowired
    private LibraryReferenceCleaner cleaner;

    @Autowired
    private SpellRepository spellRepository;

    @Autowired
    private SheetSpellReferenceRepository sheetSpellRefRepository;

    @Autowired
    private CharacterSheetRepository sheetRepository;

    @Autowired
    private PartyMemberRepository partyMemberRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    private Spell spell;
    private CharacterSheet sheet;

    @BeforeEach
    void setUp() {
        sheetSpellRefRepository.deleteAll();
        sheetRepository.deleteAll();
        partyMemberRepository.deleteAll();
        spellRepository.deleteAll();
        campaignRepository.deleteAll();

        Campaign campaign = new Campaign();
        campaign.setName("Test");
        campaign = campaignRepository.save(campaign);

        spell = new Spell();
        spell.setSource(ContentSource.SRD);
        spell.setName("Test Spell");
        spell.setSourceKey("test-spell");
        spell.setLevel(1);
        spell.setSchool("Evocation");
        spell.setDescription("Test");
        spell = spellRepository.save(spell);

        PartyMember member = new PartyMember();
        member.setCampaign(campaign);
        member.setCharacterName("Test");
        member.setAc(10);
        member.setMaxHp(10);
        member.setInitiativeBonus(0);
        member.setSpeed(30);
        member.setPassivePerception(10);
        member.setPassiveInsight(10);
        member.setPassiveInvestigation(10);
        member = partyMemberRepository.save(member);

        sheet = new CharacterSheet();
        sheet.setPartyMember(member);
        sheet = sheetRepository.save(sheet);
    }

    @Test
    void shouldCountZeroSpellReferencesWhenNoneExist() {
        assertThat(cleaner.countSpellReferences(spell.getId())).isZero();
    }

    @Test
    void shouldCountSpellReferences() {
        SheetSpellReference ref = new SheetSpellReference();
        ref.setSheet(sheet);
        ref.setSpell(spell);
        sheetSpellRefRepository.save(ref);

        assertThat(cleaner.countSpellReferences(spell.getId())).isEqualTo(1);
    }

    @Test
    void shouldCountZeroAfterDeletingReference() {
        SheetSpellReference ref = new SheetSpellReference();
        ref.setSheet(sheet);
        ref.setSpell(spell);
        ref = sheetSpellRefRepository.save(ref);

        sheetSpellRefRepository.delete(ref);
        assertThat(cleaner.countSpellReferences(spell.getId())).isZero();
    }
}
