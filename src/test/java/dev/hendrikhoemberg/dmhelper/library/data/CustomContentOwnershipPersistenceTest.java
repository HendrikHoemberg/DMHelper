package dev.hendrikhoemberg.dmhelper.library.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationConfidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CustomContentOwnershipPersistenceTest {

    @Autowired SpellRepository spells;
    @Autowired CampaignRepository campaigns;

    @BeforeEach
    void setUp() {
        spells.deleteAll();
        campaigns.deleteAll();
    }

    @Test
    void campaignScopedCustomSpellRoundTripsOwnershipAndProvenance() {
        Campaign campaign = new Campaign();
        campaign.setName("Provenance Campaign");
        campaign = campaigns.save(campaign);

        Spell spell = new Spell();
        spell.setSource(ContentSource.CUSTOM);
        spell.setCampaign(campaign);
        spell.setSourceKey("homebrew-arc-bolt");
        spell.setName("Arc Bolt");
        spell.setLevel(1);
        spell.setSchool("Evocation");
        spell.setCastingTime("1 action");
        spell.setRange("60 feet");
        spell.setComponents("V, S");
        spell.setDuration("Instantaneous");
        spell.setDescription("A crackling bolt.");
        spell.setRitual(false);
        spell.setConcentration(false);

        ContentProvenance p = new ContentProvenance();
        p.setSourceTitle("Homebrew Codex");
        p.setEditionVersion("1.0");
        p.setSourceLocator("p.12");
        p.setLicenseClassification(LicenseClassification.ORIGINAL);
        p.setExtractionConfidence(SourceAnnotationConfidence.HIGH);
        spell.setProvenance(p);

        Spell saved = spells.saveAndFlush(spell);

        Spell loaded = spells.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getSource()).isEqualTo(ContentSource.CUSTOM);
        assertThat(loaded.getCampaign().getId()).isEqualTo(campaign.getId());
        assertThat(loaded.getProvenance().getSourceTitle()).isEqualTo("Homebrew Codex");
        assertThat(loaded.getProvenance().getLicenseClassification())
                .isEqualTo(LicenseClassification.ORIGINAL);
    }

    @Test
    void srdSpellRemainsCatalogAddressableWithoutCampaign() {
        Spell spell = new Spell();
        spell.setSource(ContentSource.SRD);
        spell.setSourceKey("srd-fireball");
        spell.setName("Fireball");
        spell.setLevel(3);
        spell.setSchool("Evocation");
        spell.setCastingTime("1 action");
        spell.setRange("150 feet");
        spell.setComponents("V, S, M");
        spell.setDuration("Instantaneous");
        spell.setDescription("A bright streak flashes.");
        spell.setRitual(false);
        spell.setConcentration(false);
        spells.saveAndFlush(spell);

        Spell loaded = spells.findBySourceAndSourceKey(ContentSource.SRD, "srd-fireball")
                .orElseThrow();

        assertThat(loaded.getSource()).isEqualTo(ContentSource.SRD);
        assertThat(loaded.getCampaign()).isNull();
        assertThat(loaded.getProvenance()).isNull();
    }
}
