package dev.hendrikhoemberg.dmhelper.handout.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({HandoutService.class, SceneRefCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class})
class HandoutServiceTest {

    @Autowired private HandoutService service;
    @Autowired private jakarta.persistence.EntityManager em;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        Campaign c = new Campaign();
        c.setName("Test Campaign");
        em.persist(c);
        em.flush();
        campaignId = c.getId();
    }

    @Test
    void shouldCreateAndFindHandout() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "map.png", "image/png", "fake-data".getBytes());
        Handout h = service.create(campaignId, "The Map", "quest", file);

        assertThat(h.getId()).isNotNull();
        assertThat(h.getTitle()).isEqualTo("The Map");
        assertThat(h.getTags()).isEqualTo("quest");
        assertThat(h.getContentType()).isEqualTo("image/png");
        assertThat(h.isDmOnly()).isTrue();
        assertThat(h.isPresented()).isFalse();
    }

    @Test
    void shouldFindByCampaignId() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("f", "a.png", "image/png", "a".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("f", "b.png", "image/png", "b".getBytes());
        service.create(campaignId, "B", "", file1);
        service.create(campaignId, "A", "", file2);

        var handouts = service.findByCampaignId(campaignId);
        assertThat(handouts).hasSize(2);
        assertThat(handouts.get(0).getTitle()).isEqualTo("A");
    }

    @Test
    void shouldSetPresented() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", "test.png", "image/png", "data".getBytes());
        Handout h = service.create(campaignId, "Test", "", file);

        Handout presented = service.setPresented(h.getId(), true);
        assertThat(presented.isPresented()).isTrue();
        assertThat(presented.isDmOnly()).isFalse();
    }

    @Test
    void markingPresentedHandoutDmOnlyAlsoUnpublishesIt() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", "test.png", "image/png", "data".getBytes());
        Handout handout = service.create(campaignId, "Test", "", file);
        service.setPresented(handout.getId(), true);

        Handout secret = service.setDmOnly(handout.getId(), true);

        assertThat(secret.isDmOnly()).isTrue();
        assertThat(secret.isPresented()).isFalse();
    }

    @Test
    void shouldDeleteHandout() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", "del.png", "image/png", "data".getBytes());
        Handout h = service.create(campaignId, "To Delete", "", file);

        service.delete(h.getId());

        assertThatThrownBy(() -> service.findById(h.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowWhenHandoutNotFound() {
        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createImportedRejectsUnsupportedContentType() {
        assertThatThrownBy(() ->
                service.createImported(campaignId, "Bad", "", "f.svg", "image/svg+xml", "x".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported handout content type");
    }

    @Test
    void createImportedOriginalFileNameNeverBecomesStoragePath() throws Exception {
        Handout h = service.createImported(campaignId, "Path Traversal", "",
                "../../outside.png", "image/png", "data".getBytes());
        assertThat(h.getFileName()).doesNotContain("..").doesNotContain("/");
        // Verify the file was stored inside the files directory
        Path stored = Path.of(System.getProperty("user.home"), ".dmhelper", "files", h.getFileName());
        assertThat(stored).exists();
        assertThat(stored.toAbsolutePath().normalize().startsWith(
                Path.of(System.getProperty("user.home"), ".dmhelper", "files"))).isTrue();
    }

    @Test
    void createImportedAbsolutePathNeverBecomesStoragePath() throws Exception {
        Handout h = service.createImported(campaignId, "Absolute", "",
                "/tmp/absolute.png", "image/png", "data".getBytes());
        assertThat(h.getFileName()).doesNotContain("/");
        Path stored = Path.of(System.getProperty("user.home"), ".dmhelper", "files", h.getFileName());
        assertThat(stored).exists();
        assertThat(stored.toAbsolutePath().normalize().startsWith(
                Path.of(System.getProperty("user.home"), ".dmhelper", "files"))).isTrue();
    }

    @Test
    void createImportedDuplicateOriginalNamesProduceDistinctStorageNames() throws Exception {
        String title = "Duplicate";
        String data = "same-name-data";
        Handout h1 = service.createImported(campaignId, title, "", "duplicate.png", "image/png", data.getBytes());
        Handout h2 = service.createImported(campaignId, title, "", "duplicate.png", "image/png", data.getBytes());

        assertThat(h1.getFileName()).isNotEqualTo(h2.getFileName());

        Pattern uuidPattern = Pattern.compile(
                "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(png|jpg|gif|webp)$");
        assertThat(h1.getFileName()).matches(uuidPattern);
        assertThat(h2.getFileName()).matches(uuidPattern);
    }

    @Test
    void createImportedStreamsValidatedSourceAndExposesFileSource() throws Exception {
        byte[] bytes = "validated-stream".getBytes();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));

        Handout handout = service.createImported(campaignId, "Stream", "", "display.png", "image/png",
                new ByteArrayResource(bytes), bytes.length, digest);

        assertThat(service.getFileSource(handout.getId()).getInputStream().readAllBytes()).isEqualTo(bytes);
    }

    @Test
    void createImportedDeletesStreamWhenDescriptorDoesNotMatch() throws Exception {
        byte[] bytes = "changed".getBytes();
        Path filesDir = Path.of(System.getProperty("user.home"), ".dmhelper", "files");
        long before = countFiles(filesDir);

        assertThatThrownBy(() -> service.createImported(campaignId, "Mismatch", "", "display.png", "image/png",
                new ByteArrayResource(bytes), bytes.length, "0".repeat(64)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("validated descriptor");

        long after = countFiles(filesDir);
        assertThat(after).isEqualTo(before);
    }

    private static long countFiles(Path directory) throws IOException {
        if (!Files.exists(directory)) return 0;
        try (var files = Files.list(directory)) {
            return files.count();
        }
    }

    @Test
    void createImportedRollsBackOnWriteFailure() throws Exception {
        Path filesDir = Path.of(System.getProperty("user.home"), ".dmhelper", "files");
        Files.createDirectories(filesDir);
        filesDir.toFile().setWritable(false);
        try {
            assertThatThrownBy(() ->
                    service.createImported(campaignId, "Rollback", "", "img.png", "image/png", "content".getBytes()))
                    .isInstanceOf(IOException.class);

            em.flush();
            em.clear();

            assertThat(service.findByCampaignId(campaignId)).isEmpty();
        } finally {
            filesDir.toFile().setWritable(true);
        }
    }
}
