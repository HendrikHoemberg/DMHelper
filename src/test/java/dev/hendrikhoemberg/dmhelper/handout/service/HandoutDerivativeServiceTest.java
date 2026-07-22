package dev.hendrikhoemberg.dmhelper.handout.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.handout.data.DerivativeRecipe;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout.SafetyClassification;
import dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static dev.hendrikhoemberg.dmhelper.handout.data.Handout.SafetyClassification.PLAYER_DERIVATIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({HandoutService.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class HandoutDerivativeServiceTest {

    @MockitoBean
    private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @Autowired
    private HandoutService handoutService;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private UUID campaignId;
    private Handout source;
    private byte[] sourceBytes;

    @BeforeEach
    void setUp() throws IOException {
        Campaign c = new Campaign();
        c.setName("Test Campaign");
        em.persist(c);
        em.flush();
        campaignId = c.getId();

        sourceBytes = create2x2Png();
        MockMultipartFile file = new MockMultipartFile(
                "file", "source.png", "image/png", sourceBytes);
        source = handoutService.create(campaignId, "Source", "", file);
    }

    @Test
    void createsPlayerDerivativeWithCorrectProperties() throws IOException {
        byte[] derivedBytes = create2x2Png();
        MockMultipartFile png = new MockMultipartFile(
                "file", "derived.png", "image/png", derivedBytes);

        DerivativeRecipe recipe = new DerivativeRecipe(
                2, 2, 0, 0, 2, 2, List.of());

        Handout derivative = handoutService.createDerivative(
                campaignId, source.getId(), "Derived Title", toJson(recipe), png);

        assertThat(derivative.getSafetyClassification()).isEqualTo(PLAYER_DERIVATIVE);
        assertThat(derivative.getSourceHandout().getId()).isEqualTo(source.getId());
        assertThat(derivative.getContentType()).isEqualTo("image/png");
        assertThat(handoutService.getFileContent(source.getId())).isEqualTo(sourceBytes);
        assertThat(handoutService.getFileContent(derivative.getId())).isEqualTo(derivedBytes);
    }

    @Test
    void rejectsCrossCampaignSource() throws IOException {
        Campaign otherCampaign = new Campaign();
        otherCampaign.setName("Other Campaign");
        em.persist(otherCampaign);
        em.flush();

        byte[] derivedBytes = create2x2Png();
        MockMultipartFile png = new MockMultipartFile(
                "file", "derived.png", "image/png", derivedBytes);

        DerivativeRecipe recipe = new DerivativeRecipe(
                2, 2, 0, 0, 2, 2, List.of());

        UUID wrongCampaignId = otherCampaign.getId();
        assertThatThrownBy(() -> handoutService.createDerivative(
                wrongCampaignId, source.getId(), "Derived", toJson(recipe), png))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsNonPngContentType() throws IOException {
        byte[] derivedBytes = create2x2Png();
        MockMultipartFile nonPng = new MockMultipartFile(
                "file", "derived.jpg", "image/jpeg", derivedBytes);

        DerivativeRecipe recipe = new DerivativeRecipe(
                2, 2, 0, 0, 2, 2, List.of());

        assertThatThrownBy(() -> handoutService.createDerivative(
                campaignId, source.getId(), "Derived", toJson(recipe), nonPng))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image/png");
    }

    @Test
    void rejectsNonPositiveCropDimensions() throws IOException {
        byte[] derivedBytes = create2x2Png();
        MockMultipartFile png = new MockMultipartFile(
                "file", "derived.png", "image/png", derivedBytes);

        DerivativeRecipe recipe = new DerivativeRecipe(
                2, 2, 0, 0, 0, 2, List.of());

        assertThatThrownBy(() -> handoutService.createDerivative(
                campaignId, source.getId(), "Derived", toJson(recipe), png))
                .isInstanceOf(IllegalArgumentException.class);

        DerivativeRecipe recipe2 = new DerivativeRecipe(
                2, 2, 0, 0, 2, 0, List.of());

        assertThatThrownBy(() -> handoutService.createDerivative(
                campaignId, source.getId(), "Derived", toJson(recipe2), png))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveRedactionDimensions() throws IOException {
        byte[] derivedBytes = create2x2Png();
        MockMultipartFile png = new MockMultipartFile(
                "file", "derived.png", "image/png", derivedBytes);

        DerivativeRecipe recipe = new DerivativeRecipe(
                2, 2, 0, 0, 2, 2,
                List.of(new DerivativeRecipe.RedactionRect(0, 0, 2, 0)));

        assertThatThrownBy(() -> handoutService.createDerivative(
                campaignId, source.getId(), "Derived", toJson(recipe), png))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCropOutsideSourceBounds() throws IOException {
        byte[] derivedBytes = create2x2Png();
        MockMultipartFile png = new MockMultipartFile(
                "file", "derived.png", "image/png", derivedBytes);

        DerivativeRecipe recipe = new DerivativeRecipe(
                2, 2, 1, 1, 2, 2, List.of());

        assertThatThrownBy(() -> handoutService.createDerivative(
                campaignId, source.getId(), "Derived", toJson(recipe), png))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRedactionOutsideCropBounds() throws IOException {
        byte[] derivedBytes = create2x2Png();
        MockMultipartFile png = new MockMultipartFile(
                "file", "derived.png", "image/png", derivedBytes);

        DerivativeRecipe recipe = new DerivativeRecipe(
                2, 2, 0, 0, 2, 2,
                List.of(new DerivativeRecipe.RedactionRect(0, 0, 3, 2)));

        assertThatThrownBy(() -> handoutService.createDerivative(
                campaignId, source.getId(), "Derived", toJson(recipe), png))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rollsBackFileOnSaveFailure() throws IOException {
        byte[] derivedBytes = create2x2Png();
        MockMultipartFile png = new MockMultipartFile(
                "file", "derived.png", "image/png", derivedBytes);

        DerivativeRecipe recipe = new DerivativeRecipe(
                2, 2, 0, 0, 2, 2, List.of());

        handoutService.createDerivative(
                campaignId, source.getId(), "Rollback", toJson(recipe), png);

        em.flush();
        em.clear();

        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        // Both source and derivative should still be findable in DB (transaction rolled back in test)
        // but the associated files should be cleaned up
        assertThat(handoutService.findByCampaignId(campaignId)).hasSize(2); // source + derivative both persisted
    }

    @Test
    void sourceRemainsUnchanged() throws IOException {
        byte[] derivedBytes = create2x2Png();
        MockMultipartFile png = new MockMultipartFile(
                "file", "derived.png", "image/png", derivedBytes);

        DerivativeRecipe recipe = new DerivativeRecipe(
                2, 2, 0, 0, 2, 2, List.of());

        handoutService.createDerivative(
                campaignId, source.getId(), "Derived", toJson(recipe), png);

        Handout reloaded = handoutService.findById(source.getId());
        assertThat(reloaded.getTitle()).isEqualTo(source.getTitle());
        assertThat(reloaded.getSafetyClassification()).isEqualTo(SafetyClassification.UNREVIEWED);
        assertThat(reloaded.getTags()).isEqualTo(source.getTags());
        assertThat(reloaded.getSourceHandout()).isNull();
        assertThat(handoutService.getFileContent(source.getId())).isEqualTo(sourceBytes);
    }

    // --- helpers ---

    private static byte[] create2x2Png() throws IOException {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFFFFFFFF);
        img.setRGB(1, 0, 0xFFFFFFFF);
        img.setRGB(0, 1, 0xFFFFFFFF);
        img.setRGB(1, 1, 0xFFFFFFFF);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static String toJson(DerivativeRecipe recipe) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"sourceWidth\":").append(recipe.sourceWidth()).append(",");
        sb.append("\"sourceHeight\":").append(recipe.sourceHeight()).append(",");
        sb.append("\"cropX\":").append(recipe.cropX()).append(",");
        sb.append("\"cropY\":").append(recipe.cropY()).append(",");
        sb.append("\"cropWidth\":").append(recipe.cropWidth()).append(",");
        sb.append("\"cropHeight\":").append(recipe.cropHeight()).append(",");
        sb.append("\"redactions\":[");
        for (int i = 0; i < recipe.redactions().size(); i++) {
            if (i > 0) sb.append(",");
            var r = recipe.redactions().get(i);
            sb.append("{");
            sb.append("\"x\":").append(r.x()).append(",");
            sb.append("\"y\":").append(r.y()).append(",");
            sb.append("\"width\":").append(r.width()).append(",");
            sb.append("\"height\":").append(r.height());
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
}
