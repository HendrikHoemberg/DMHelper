package dev.hendrikhoemberg.dmhelper.audio.data;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocation;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AudioCuePersistenceTest {

    @Autowired private EntityManager em;
    @Autowired private AudioCueRepository audioCueRepository;
    @Autowired private SessionAudioStateRepository sessionAudioStateRepository;

    private Campaign campaign;
    private AudioCue cue;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Audio Test Campaign");
        em.persist(campaign);

        cue = new AudioCue();
        cue.setCampaign(campaign);
        cue.setCueKey("tavern-ambience");
        cue.setName("Tavern Ambience");
        cue.setProviderId("yt_abc123");
        cue.setReferenceKind(AudioReferenceKind.VIDEO);
        cue.setProviderReference("https://example.com/watch?v=abc123");
        cue.setCachedTitle("Cozy Tavern Fireplace");
        cue.setArtistOrOwner("Ambient Mixes");
        cue.setArtworkUrl("https://example.com/art.jpg");
        cue.setDurationSeconds(3600);
        cue.setCategory(AudioCategory.AMBIENT);
        cue.setVolumeHint(75);
        cue.setTransitionPreference(AudioTransitionPreference.CROSSFADE);
        cue.setNotes("Good for starting tavern scenes");
        em.persist(cue);
        em.flush();
        em.clear();
    }

    @Test
    void persistsAndReloadsCompleteAudioCue() {
        AudioCue loaded = audioCueRepository.findDetailedById(cue.getId()).orElseThrow();

        assertThat(loaded.getCampaign().getId()).isEqualTo(campaign.getId());
        assertThat(loaded.getCueKey()).isEqualTo("tavern-ambience");
        assertThat(loaded.getName()).isEqualTo("Tavern Ambience");
        assertThat(loaded.getProviderId()).isEqualTo("yt_abc123");
        assertThat(loaded.getReferenceKind()).isEqualTo(AudioReferenceKind.VIDEO);
        assertThat(loaded.getProviderReference()).isEqualTo("https://example.com/watch?v=abc123");
        assertThat(loaded.getCachedTitle()).isEqualTo("Cozy Tavern Fireplace");
        assertThat(loaded.getArtistOrOwner()).isEqualTo("Ambient Mixes");
        assertThat(loaded.getArtworkUrl()).isEqualTo("https://example.com/art.jpg");
        assertThat(loaded.getDurationSeconds()).isEqualTo(3600);
        assertThat(loaded.getCategory()).isEqualTo(AudioCategory.AMBIENT);
        assertThat(loaded.getVolumeHint()).isEqualTo(75);
        assertThat(loaded.getTransitionPreference()).isEqualTo(AudioTransitionPreference.CROSSFADE);
        assertThat(loaded.getNotes()).isEqualTo("Good for starting tavern scenes");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void persistsCampaignDefaultAudioCueAssignment() {
        campaign.setDefaultAudioCue(cue);
        em.merge(campaign);
        em.flush();
        em.clear();

        Campaign loaded = em.find(Campaign.class, campaign.getId());
        assertThat(loaded.getDefaultAudioCue()).isNotNull();
        assertThat(loaded.getDefaultAudioCue().getId()).isEqualTo(cue.getId());
    }

    @Test
    void persistsSceneAudioCueAssignment() {
        Adventure adventure = new Adventure();
        adventure.setCampaign(campaign);
        adventure.setName("Test Adventure");
        adventure.setSortOrder(0);
        em.persist(adventure);

        Chapter chapter = new Chapter();
        chapter.setAdventure(adventure);
        chapter.setTitle("Test Chapter");
        em.persist(chapter);

        Scene scene = new Scene();
        scene.setChapter(chapter);
        scene.setTitle("Test Scene");
        scene.setSortOrder(0);
        scene.setSceneAudioCue(cue);
        em.persist(scene);
        em.flush();
        em.clear();

        Scene loaded = em.find(Scene.class, scene.getId());
        assertThat(loaded.getSceneAudioCue()).isNotNull();
        assertThat(loaded.getSceneAudioCue().getId()).isEqualTo(cue.getId());
    }

    @Test
    void persistsEncounterAudioCueAssignments() {
        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setName("Test Encounter");
        encounter.setCombatAudioCue(cue);
        encounter.setVictoryAudioCue(cue);
        encounter.setVictoryCueDurationSeconds(30);
        em.persist(encounter);
        em.flush();
        em.clear();

        Encounter loaded = em.find(Encounter.class, encounter.getId());
        assertThat(loaded.getCombatAudioCue()).isNotNull();
        assertThat(loaded.getCombatAudioCue().getId()).isEqualTo(cue.getId());
        assertThat(loaded.getVictoryAudioCue()).isNotNull();
        assertThat(loaded.getVictoryAudioCue().getId()).isEqualTo(cue.getId());
        assertThat(loaded.getVictoryCueDurationSeconds()).isEqualTo(30);
    }

    @Test
    void persistsWorldLocationAudioCueAssignment() {
        WorldLocation location = new WorldLocation();
        location.setCampaign(campaign);
        location.setName("Test Location");
        location.setLocationAudioCue(cue);
        em.persist(location);
        em.flush();
        em.clear();

        WorldLocation loaded = em.find(WorldLocation.class, location.getId());
        assertThat(loaded.getLocationAudioCue()).isNotNull();
        assertThat(loaded.getLocationAudioCue().getId()).isEqualTo(cue.getId());
    }

    @Test
    void persistsAndReloadsSessionAudioState() {
        CampaignSession session = CampaignSession.idle(campaign);
        em.persist(session);
        em.flush();

        SessionAudioState state = new SessionAudioState();
        state.setSession(session);
        state.setManualOverrideCue(cue);
        state.setAcceptedAutomaticCue(cue);
        state.setPendingCue(cue);
        state.setDismissedCandidateCue(cue);
        state.setMuted(true);
        state.setTemporaryVictoryCue(cue);
        em.persist(state);
        em.flush();
        em.clear();

        SessionAudioState loaded = sessionAudioStateRepository.findBySessionId(session.getId()).orElseThrow();
        assertThat(loaded.getSession().getId()).isEqualTo(session.getId());
        assertThat(loaded.getManualOverrideCue().getId()).isEqualTo(cue.getId());
        assertThat(loaded.getAcceptedAutomaticCue().getId()).isEqualTo(cue.getId());
        assertThat(loaded.getPendingCue().getId()).isEqualTo(cue.getId());
        assertThat(loaded.getDismissedCandidateCue().getId()).isEqualTo(cue.getId());
        assertThat(loaded.isMuted()).isTrue();
        assertThat(loaded.getTemporaryVictoryCue().getId()).isEqualTo(cue.getId());
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void cueKeyUniquenessPerCampaign() {
        AudioCue sameKeyDifferentCampaign = new AudioCue();
        sameKeyDifferentCampaign.setCampaign(campaign);
        sameKeyDifferentCampaign.setCueKey("tavern-ambience");
        sameKeyDifferentCampaign.setName("Duplicate Key");
        sameKeyDifferentCampaign.setReferenceKind(AudioReferenceKind.VIDEO);
        sameKeyDifferentCampaign.setProviderReference("https://example.com/other");
        sameKeyDifferentCampaign.setCategory(AudioCategory.AMBIENT);
        sameKeyDifferentCampaign.setTransitionPreference(AudioTransitionPreference.CUT);

        assertThatThrownBy(() -> {
            em.persist(sameKeyDifferentCampaign);
            em.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    void repositoryFindByCampaignIdOrdered() {
        AudioCue cue2 = new AudioCue();
        cue2.setCampaign(campaign);
        cue2.setCueKey("combat-theme");
        cue2.setName("Combat Theme");
        cue2.setReferenceKind(AudioReferenceKind.VIDEO);
        cue2.setProviderReference("https://example.com/combat");
        cue2.setCategory(AudioCategory.COMBAT);
        cue2.setTransitionPreference(AudioTransitionPreference.CUT);
        em.persist(cue2);
        em.flush();
        em.clear();

        assertThat(audioCueRepository.findByCampaignIdOrderByNameAsc(campaign.getId()))
                .extracting(AudioCue::getName)
                .containsExactly("Combat Theme", "Tavern Ambience");
    }

    @Test
    void repositoryFindByCampaignIdAndCueKey() {
        assertThat(audioCueRepository.findByCampaignIdAndCueKey(campaign.getId(), "tavern-ambience"))
                .isPresent();
        assertThat(audioCueRepository.findByCampaignIdAndCueKey(campaign.getId(), "nonexistent"))
                .isEmpty();
    }

    @Test
    void repositoryFindByReferenceKindAndProviderReference() {
        assertThat(audioCueRepository.findByReferenceKindAndProviderReferenceAndCampaignId(
                AudioReferenceKind.VIDEO, "https://example.com/watch?v=abc123", campaign.getId()))
                .hasSize(1);
        assertThat(audioCueRepository.findByReferenceKindAndProviderReferenceAndCampaignId(
                AudioReferenceKind.PLAYLIST, "https://example.com/watch?v=abc123", campaign.getId()))
                .isEmpty();
    }

    @Test
    void alternativeAudioCueValuesPersistCorrectly() {
        AudioCue playlistCue = new AudioCue();
        playlistCue.setCampaign(campaign);
        playlistCue.setCueKey("battle-music");
        playlistCue.setName("Battle Music Playlist");
        playlistCue.setReferenceKind(AudioReferenceKind.PLAYLIST);
        playlistCue.setProviderReference("https://example.com/playlist/xyz");
        playlistCue.setCategory(AudioCategory.COMBAT);
        playlistCue.setTransitionPreference(AudioTransitionPreference.CUT);
        playlistCue.setVolumeHint(null);
        em.persist(playlistCue);
        em.flush();
        em.clear();

        AudioCue loaded = audioCueRepository.findByCampaignIdAndCueKey(campaign.getId(), "battle-music")
                .orElseThrow();
        assertThat(loaded.getReferenceKind()).isEqualTo(AudioReferenceKind.PLAYLIST);
        assertThat(loaded.getCategory()).isEqualTo(AudioCategory.COMBAT);
        assertThat(loaded.getTransitionPreference()).isEqualTo(AudioTransitionPreference.CUT);
        assertThat(loaded.getVolumeHint()).isNull();
    }
}
