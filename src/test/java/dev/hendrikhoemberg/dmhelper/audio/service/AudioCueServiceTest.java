package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioStateRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AudioCueService.class, AudioCueValidator.class, AudioCueDependencyService.class})
class AudioCueServiceTest {

    @Autowired private AudioCueService service;
    @Autowired private AudioCueRepository repository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private EntityManager em;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        campaignRepository.deleteAll();

        Campaign campaign = new Campaign();
        campaign.setName("Audio Campaign");
        campaign = campaignRepository.save(campaign);
        campaignId = campaign.getId();
        em.flush();
    }

    @Test
    void createsCueInCampaign() {
        AudioCueWrite write = validWrite("cave-ambient", "Cave Ambient");
        AudioCue cue = service.create(campaignId, write);
        assertThat(cue.getId()).isNotNull();
        assertThat(cue.getCueKey()).isEqualTo("cave-ambient");
        assertThat(cue.getName()).isEqualTo("Cave Ambient");
        assertThat(cue.getCampaign().getId()).isEqualTo(campaignId);
    }

    @Test
    void listsCuesByCampaign() {
        service.create(campaignId, validWrite("a", "Alpha"));
        service.create(campaignId, validWrite("b", "Beta"));
        List<AudioCue> cues = service.listByCampaign(campaignId);
        assertThat(cues).hasSize(2);
        assertThat(cues).extracting(AudioCue::getName).containsExactly("Alpha", "Beta");
    }

    @Test
    void listsEmptyForUnknownCampaign() {
        List<AudioCue> cues = service.listByCampaign(UUID.randomUUID());
        assertThat(cues).isEmpty();
    }

    @Test
    void findsCueById() {
        AudioCue cue = service.create(campaignId, validWrite("test", "Test"));
        AudioCue found = service.findById(cue.getId());
        assertThat(found.getName()).isEqualTo("Test");
    }

    @Test
    void throwsNotFoundForMissingId() {
        assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findsCueByCampaignAndKey() {
        service.create(campaignId, validWrite("my-key", "My Key"));
        AudioCue found = service.findByCampaignAndKey(campaignId, "my-key");
        assertThat(found.getName()).isEqualTo("My Key");
    }

    @Test
    void throwsNotFoundForMissingKey() {
        assertThatThrownBy(() -> service.findByCampaignAndKey(campaignId, "nonexistent"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatesCue() {
        AudioCue cue = service.create(campaignId, validWrite("original", "Original"));
        AudioCueWrite update = new AudioCueWrite(
                "updated", "Updated", "youtube", AudioReferenceKind.VIDEO,
                "dQw4w9WgXcQ", "New Title", "New Artist", null, 180,
                AudioCategory.COMBAT, 90, AudioTransitionPreference.CUT,
                "Updated notes", cue.getId());
        AudioCue updated = service.update(cue.getId(), update, campaignId);
        assertThat(updated.getName()).isEqualTo("Updated");
        assertThat(updated.getCueKey()).isEqualTo("updated");
        assertThat(updated.getCategory()).isEqualTo(AudioCategory.COMBAT);
    }

    @Test
    void rejectsUpdateWithDifferentCampaignId() {
        AudioCue cue = service.create(campaignId, validWrite("original", "Original"));
        UUID otherCampaign = UUID.randomUUID();
        AudioCueWrite write = validWrite("original", "Original");
        assertThatThrownBy(() -> service.update(cue.getId(), write, otherCampaign))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clonesCue() {
        AudioCue original = service.create(campaignId, validWrite("original", "Original"));
        AudioCue clone = service.cloneCue(original.getId(), campaignId, "cloned-key");
        assertThat(clone.getId()).isNotEqualTo(original.getId());
        assertThat(clone.getCueKey()).isEqualTo("cloned-key");
        assertThat(clone.getName()).isEqualTo("Original");
    }

    @Test
    void deletionImpactShowsDependencies() {
        AudioCue cue = service.create(campaignId, validWrite("to-delete", "To Delete"));
        AudioCueDeletionImpact impact = service.computeDeletionImpact(cue.getId());
        assertThat(impact.hasDependents()).isFalse();
        assertThat(impact.cueId()).isEqualTo(cue.getId());
    }

    @Test
    void deletesCue() {
        AudioCue cue = service.create(campaignId, validWrite("to-delete", "To Delete"));
        service.deleteCue(cue.getId(), true);
        assertThat(repository.findById(cue.getId())).isEmpty();
    }

    @Test
    void rejectsDeletingNonexistentCue() {
        assertThatThrownBy(() -> service.deleteCue(UUID.randomUUID(), true))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsMissingCampaignOnCreate() {
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), validWrite("x", "X")))
                .isInstanceOf(NotFoundException.class);
    }

    private AudioCueWrite validWrite(String cueKey, String name) {
        return new AudioCueWrite(
                cueKey, name, "youtube", AudioReferenceKind.VIDEO,
                "dQw4w9WgXcQ", null, null, null, null,
                AudioCategory.AMBIENT, null, AudioTransitionPreference.CROSSFADE,
                null, null);
    }
}
