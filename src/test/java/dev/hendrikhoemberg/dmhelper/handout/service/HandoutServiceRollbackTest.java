package dev.hendrikhoemberg.dmhelper.handout.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandoutServiceRollbackTest {

    @TempDir Path tempDir;

    private final HandoutRepository handoutRepository = mock(HandoutRepository.class);
    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);
    private final SceneRefCleaner sceneRefCleaner = mock(SceneRefCleaner.class);
    private final UUID campaignId = UUID.randomUUID();
    private HandoutService service;

    @BeforeEach
    void setUp() {
        Campaign campaign = new Campaign();
        campaign.setName("Rollback campaign");
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        service = new HandoutService(handoutRepository, campaignRepository, sceneRefCleaner,
                tempDir.toString());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void removesImportedFileWhenRepositorySaveFails() {
        when(handoutRepository.save(any(Handout.class)))
                .thenThrow(new IllegalStateException("database failure"));

        assertThatThrownBy(() -> service.createImported(campaignId, "Failed", "",
                "failed.png", "image/png", "content".getBytes()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database failure");

        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void removesImportedFileWhenOuterTransactionRollsBack() throws Exception {
        when(handoutRepository.save(any(Handout.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        Handout handout = service.createImported(campaignId, "Rolled back", "",
                "rollback.png", "image/png", "content".getBytes());
        Path storedFile = filesDir().resolve(handout.getFileName());
        assertThat(storedFile).exists();

        TransactionSynchronizationManager.getSynchronizations().forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(storedFile).doesNotExist();
    }

    private Path filesDir() {
        return tempDir.resolve(".dmhelper").resolve("files");
    }

    private java.util.List<Path> storedFiles() {
        if (!Files.isDirectory(filesDir())) return java.util.List.of();
        try (var files = Files.list(filesDir())) {
            return files.toList();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
