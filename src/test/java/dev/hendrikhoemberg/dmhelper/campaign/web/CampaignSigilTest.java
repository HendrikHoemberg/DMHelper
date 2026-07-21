package dev.hendrikhoemberg.dmhelper.campaign.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class CampaignSigilTest {

    @MockitoBean
    private CampaignRepository campaignRepository;

  @Test
  void sameIdAlwaysProducesSameSigil() {
    UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    assertThat(CampaignSigil.from(id)).isEqualTo(CampaignSigil.from(id));
  }

  @Test
  void differentIdsProduceDifferentGeometry() {
    UUID firstId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    UUID secondId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

    assertThat(CampaignSigil.from(firstId)).isNotEqualTo(CampaignSigil.from(secondId));
  }

  @Test
  void generatedPathUsesOnlyNormalizedSvgCoordinates() {
    CampaignSigil sigil = CampaignSigil.from(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));

    assertThat(sigil.path()).matches("M 50 10(?: L [1-8][0-9] [1-8][0-9]){5} Z");
    assertThat(sigil.rotation()).isIn(0, 45, 90, 135);
  }
}
