package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreviewStore;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationPipeline;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CampaignImportCoordinatorTest {

    @Autowired CampaignPackageValidationPipeline pipeline;
    @Autowired CampaignImportPreviewStore previews;
    @Autowired CampaignImportCoordinator coordinator;
    @Autowired CampaignService campaigns;
    @Autowired CampaignPackageKeyService keys;
    @TempDir Path temp;

    @Test
    void confirmsMigratedCurrentSurfaceWithoutLossAndBindsCampaignKey() throws Exception {
        var source = new ClassPathResource("campaigns/v1/feature-complete.dmcampaign.json");
        var staged = new CampaignPackageReader(temp).read(source.getInputStream(),
                "legacy.dmcampaign.json", "application/json");
        var result = pipeline.validate(staged);
        assertThat(result.valid()).as(result.problems().toString()).isTrue();
        var preview = previews.retain(result);

        assertThatThrownBy(() -> coordinator.confirm(preview.previewId(), false))
                .isInstanceOf(IllegalArgumentException.class);
        var campaign = coordinator.confirm(preview.previewId(), true);

        CampaignExportDto exported = JsonMapper.builder().build().readValue(
                campaigns.exportToJson(campaign.getId()), CampaignExportDto.class);
        assertThat(exported.party()).hasSize(1);
        assertThat(exported.maps()).hasSize(1);
        assertThat(exported.encounters()).hasSize(1);
        assertThat(exported.notes()).hasSize(1);
        assertThat(exported.quicknotes()).hasSize(8);
        assertThat(exported.assignments()).hasSize(1);
        assertThat(exported.ledger()).hasSize(1);
        assertThat(exported.timeline()).hasSize(1);
        assertThat(exported.adventures()).hasSize(1);
        var adv = exported.adventures().get(0);
        assertThat(adv.name()).isEqualTo("Crypt Descent");
        assertThat(adv.description()).isEqualTo("An adventure into the ancient dwarven crypt.");
        assertThat(adv.sourceAttribution()).isEqualTo("Homebrew");
        assertThat(adv.sortOrder()).isOne();
        assertThat(adv.chapters()).hasSize(1);
        var ch = adv.chapters().get(0);
        assertThat(ch.title()).isEqualTo("Chapter 1");
        assertThat(ch.intro()).isEqualTo("The party stands before the ancient doors.");
        assertThat(ch.sortOrder()).isOne();
        assertThat(ch.scenes()).hasSize(1);
        var sc = ch.scenes().get(0);
        assertThat(sc.title()).isEqualTo("Crypt Entry");
        assertThat(sc.body()).isEqualTo("The hallway is dark.");
        assertThat(sc.status()).isEqualTo("UNVISITED");
        assertThat(sc.sortOrder()).isOne();
        assertThat(sc.map()).isEqualTo("The Crypt");
        assertThat(sc.encounter()).startsWith("crypt-guardians");
        assertThat(sc.statblocks()).containsExactly("custom_goblin-captain");
        assertThat(sc.handouts()).containsExactly("Warning Plaque");
        assertThat(sc.pin()).containsEntry("x", 200).containsEntry("y", 150);
        assertThat(keys.find(campaign.getId(), CampaignContentType.CAMPAIGN, campaign.getId()))
                .contains(result.manifest().campaign().key());
        assertThat(staged.stagingDirectory()).doesNotExist();
        campaigns.delete(campaign.getId());
    }
}
