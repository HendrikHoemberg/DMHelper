package dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview;

import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessReport;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PreviewReadinessTest {

    @Test
    void previewCarriesAReadinessReport() {
        CampaignImportPreview preview = new CampaignImportPreview(
                java.util.UUID.randomUUID(), "READY", 3, 3, null, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), java.time.Instant.now(),
                new CampaignReadinessReport(List.of()));
        assertThat(preview.readiness()).isNotNull();
        assertThat(preview.readiness().sessionReady()).isTrue();
    }
}
