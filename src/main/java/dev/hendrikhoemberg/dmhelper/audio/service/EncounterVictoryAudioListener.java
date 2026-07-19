package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EncounterVictoryAudioListener {

    private static final Logger log = LoggerFactory.getLogger(EncounterVictoryAudioListener.class);

    private final AudioCueRepository cueRepository;
    private final SessionAudioStateService stateService;

    public EncounterVictoryAudioListener(AudioCueRepository cueRepository,
                                         SessionAudioStateService stateService) {
        this.cueRepository = cueRepository;
        this.stateService = stateService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterEncounterEnded(EncounterVictoryAudioRequested event) {
        try {
            cueRepository.findById(event.cueId()).ifPresent(cue -> stateService.startVictory(
                    event.campaignId(), event.encounterId(), event.encounterName(), cue,
                    event.durationSeconds()));
        } catch (RuntimeException ignored) {
            log.warn("Victory audio could not be prepared; encounter completion remains successful");
        }
    }
}
