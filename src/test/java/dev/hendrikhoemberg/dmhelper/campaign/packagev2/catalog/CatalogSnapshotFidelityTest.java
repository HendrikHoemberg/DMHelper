package dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CatalogSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CatalogSnapshotFidelityTest {

    @Autowired
    private CampaignCatalogService catalogService;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void checkedInSnapshotMatchesLiveService() throws Exception {
        CatalogSnapshot live = catalogService.snapshot();
        CatalogSnapshot file = mapper.readValue(
                new ClassPathResource("catalog/srd-5.2-catalog.json").getInputStream(),
                CatalogSnapshot.class);

        assertThat(file.version()).isEqualTo(live.version());
        assertThat(file.sha256()).isEqualTo(live.sha256());
        assertThat(file.entries()).hasSize(live.entries().size());
        assertThat(file.entries()).isEqualTo(live.entries());
    }
}
