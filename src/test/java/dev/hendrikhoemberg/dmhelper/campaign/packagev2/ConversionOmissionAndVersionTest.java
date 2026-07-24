package dev.hendrikhoemberg.dmhelper.campaign.packagev2;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ConversionOmissionDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

class ConversionOmissionAndVersionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void currentVersionIsThree() {
        assertThat(CampaignManifestV2.CURRENT_FORMAT_VERSION).isEqualTo(3);
    }

    @Test
    void metadataDefaultsOmissionsToEmpty() {
        Metadata m = new Metadata("k", null, "gen", "cat", "sha", null);
        assertThat(m.conversionOmissions()).isEmpty();
    }

    @Test
    void metadataCarriesDeclaredOmissions() {
        Metadata m = new Metadata("k", null, "gen", "cat", "sha", null,
                java.util.List.of(new ConversionOmissionDto("maps", "no printed maps in source")));
        assertThat(m.conversionOmissions()).singleElement()
                .extracting(ConversionOmissionDto::area).isEqualTo("maps");
    }

    @Test
    void v2ManifestDeserializesWithEmptyOmissions() throws Exception {
        String json;
        try (var in = new ClassPathResource("campaigns/v2/minimal.dmcampaign.json").getInputStream()) {
            json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        CampaignManifestV2 manifest = mapper.readValue(json, CampaignManifestV2.class);
        assertThat(manifest.formatVersion()).isEqualTo(2);
        assertThat(manifest.metadata().conversionOmissions()).isEmpty();
    }
}
