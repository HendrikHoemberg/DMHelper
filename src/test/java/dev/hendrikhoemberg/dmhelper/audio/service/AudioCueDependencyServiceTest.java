package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioState;
import dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioStateRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({AudioCueDependencyService.class})
class AudioCueDependencyServiceTest {

    @Autowired private AudioCueDependencyService dependencyService;
    @Autowired private AudioCueRepository audioCueRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CampaignSessionRepository sessionRepository;
    @Autowired private SessionAudioStateRepository stateRepository;
    @Autowired private EntityManager em;

    private Campaign campaign;
    private AudioCue cue;
    private CampaignSession session;

    @BeforeEach
    void setUp() {
        stateRepository.deleteAll();
        audioCueRepository.deleteAll();
        sessionRepository.deleteAll();
        campaignRepository.deleteAll();

        campaign = new Campaign();
        campaign.setName("Dep Campaign");
        campaign = campaignRepository.save(campaign);

        session = new CampaignSession();
        session.setCampaign(campaign);
        session = sessionRepository.save(session);

        cue = new AudioCue();
        cue.setCampaign(campaign);
        cue.setCueKey("dep-cue");
        cue.setName("Dep Cue");
        cue.setReferenceKind(AudioReferenceKind.VIDEO);
        cue.setProviderReference("dQw4w9WgXcQ");
        cue.setCategory(AudioCategory.AMBIENT);
        cue.setTransitionPreference(AudioTransitionPreference.CROSSFADE);
        cue = audioCueRepository.save(cue);
        em.flush();
    }

    @Test
    void emptyImpactForUnusedCue() {
        AudioCueDeletionImpact impact = dependencyService.computeDeletionImpact(cue);
        assertThat(impact.hasDependents()).isFalse();
        assertThat(impact.cueId()).isEqualTo(cue.getId());
        assertThat(impact.cueName()).isEqualTo("Dep Cue");
    }

    @Test
    void reportsSessionStateDependencies() {
        SessionAudioState state = new SessionAudioState();
        state.setSession(session);
        state.setManualOverrideCue(cue);
        state.setAcceptedAutomaticCue(cue);
        state.setPendingCue(cue);
        state.setDismissedCandidateCue(cue);
        state.setTemporaryVictoryCue(cue);
        stateRepository.save(state);
        em.flush();

        AudioCueDeletionImpact impact = dependencyService.computeDeletionImpact(cue);
        assertThat(impact.hasDependents()).isTrue();
        assertThat(impact.dependencies())
                .extracting(AudioCueDependency::kind)
                .containsExactlyInAnyOrder(
                        AudioCueDependencyService.DEP_KIND_SESSION_OVERRIDE,
                        AudioCueDependencyService.DEP_KIND_SESSION_ACCEPTED,
                        AudioCueDependencyService.DEP_KIND_SESSION_PENDING,
                        AudioCueDependencyService.DEP_KIND_SESSION_DISMISSED,
                        AudioCueDependencyService.DEP_KIND_SESSION_VICTORY);
    }
}
