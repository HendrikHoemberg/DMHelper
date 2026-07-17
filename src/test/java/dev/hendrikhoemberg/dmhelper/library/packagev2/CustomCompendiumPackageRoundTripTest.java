package dev.hendrikhoemberg.dmhelper.library.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportCoordinator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignImportCoordinator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignPackageArtifact;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.LicenseClassification;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.SpellService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReference;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CustomCompendiumPackageRoundTripTest {

    @Autowired CampaignRepository campaignRepository;
    @Autowired SpellService spellService;
    @Autowired SpellRepository spellRepository;
    @Autowired PartyMemberRepository partyMemberRepository;
    @Autowired CharacterSheetRepository characterSheetRepository;
    @Autowired SheetSpellReferenceRepository sheetSpellReferenceRepository;
    @Autowired CampaignExportCoordinator exportCoordinator;
    @Autowired CustomContentSupport customContentSupport;

    @Test
    void exportsCampaignCustomSpellAsPackageRefFromSheet() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setName("Custom Compendium Campaign");
        campaign = campaignRepository.save(campaign);

        var provenance = customContentSupport.defaultForCreate(LicenseClassification.ORIGINAL);
        provenance.setSourceTitle("Homebrew Codex");
        Spell spell = spellService.createCustom(
                campaign.getId(),
                new SpellService.SpellWrite(
                        "Arc Bolt", 1, "Evocation", "1 action", "60 feet",
                        "V, S", "Instantaneous", "A crackling bolt.", null,
                        false, false, "homebrew-arc-bolt"),
                provenance);

        PartyMember pm = new PartyMember();
        pm.setCampaign(campaign);
        pm.setCharacterName("Mira");
        pm.setPlayerName("Alex");
        pm.setClassAndLevel("Wizard 1");
        pm.setAc(12);
        pm.setMaxHp(8);
        pm.setCurrentHp(8);
        pm.setInitiativeBonus(2);
        pm.setSpeed(30);
        pm.setPassivePerception(12);
        pm.setPassiveInsight(11);
        pm.setPassiveInvestigation(13);
        pm.setActive(true);
        pm = partyMemberRepository.save(pm);

        CharacterSheet sheet = new CharacterSheet();
        sheet.setPartyMember(pm);
        sheet.setXp(0);
        sheet.setHitDiceUsed(0);
        sheet = characterSheetRepository.save(sheet);
        pm.setCharacterSheet(sheet);
        partyMemberRepository.save(pm);

        SheetSpellReference ref = new SheetSpellReference();
        ref.setSheet(sheet);
        ref.setSpell(spell);
        ref.setPrepared(true);
        sheetSpellReferenceRepository.save(ref);

        CampaignPackageArtifact artifact = exportCoordinator.export(campaign.getId());
        var manifest = artifact.manifest();

        assertThat(manifest.customSpells()).hasSize(1);
        assertThat(manifest.customSpells().get(0).sourceKey()).isEqualTo("homebrew-arc-bolt");
        assertThat(manifest.customSpells().get(0).provenance()).isNotNull();
        assertThat(manifest.customSpells().get(0).provenance().sourceTitle()).isEqualTo("Homebrew Codex");

        ContentReference spellRef = manifest.party().get(0).sheet().spells().get(0).spellRef();
        assertThat(spellRef.scope()).isEqualTo(ContentReference.Scope.PACKAGE);
        assertThat(spellRef.type()).isEqualTo(CampaignContentType.SPELL);
        assertThat(spellRef.key()).isNotBlank();
        assertThat(spellRef.sourceKey()).isNull();

        assertThat(spell.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(spell.getCampaign().getId()).isEqualTo(campaign.getId());
    }
}
