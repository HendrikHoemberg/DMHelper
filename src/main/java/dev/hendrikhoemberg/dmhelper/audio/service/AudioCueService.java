package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import dev.hendrikhoemberg.dmhelper.audio.provider.AudioProviderRegistry;
import dev.hendrikhoemberg.dmhelper.audio.provider.ParsedAudioReference;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AudioCueService {

    private final AudioCueRepository repository;
    private final CampaignRepository campaignRepository;
    private final AudioCueValidator validator;
    private final AudioCueDependencyService dependencyService;

    public AudioCueService(AudioCueRepository repository,
                           CampaignRepository campaignRepository,
                           AudioCueValidator validator,
                           AudioCueDependencyService dependencyService) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.validator = validator;
        this.dependencyService = dependencyService;
    }

    @Transactional(readOnly = true)
    public List<AudioCue> listByCampaign(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public AudioCue findById(UUID id) {
        return repository.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Audio cue not found: " + id));
    }

    @Transactional(readOnly = true)
    public AudioCue findByCampaignAndKey(UUID campaignId, String cueKey) {
        return repository.findByCampaignIdAndCueKey(campaignId, cueKey)
                .orElseThrow(() -> new NotFoundException(
                        "Audio cue not found: " + cueKey + " in campaign " + campaignId));
    }

    public AudioCue create(UUID campaignId, AudioCueWrite write) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found: " + campaignId));
        validator.validate(write, campaignId);

        AudioCue cue = new AudioCue();
        cue.setCampaign(campaign);
        applyWrite(cue, write);
        return repository.save(cue);
    }

    public AudioCue update(UUID id, AudioCueWrite write, UUID campaignId) {
        AudioCue cue = findById(id);
        if (!cue.getCampaign().getId().equals(campaignId)) {
            throw new IllegalArgumentException("Cue does not belong to the specified campaign");
        }
        validator.validate(write, campaignId);
        applyWrite(cue, write);
        return repository.save(cue);
    }

    public AudioCue cloneCue(UUID sourceId, UUID campaignId, String newCueKey) {
        AudioCue original = findById(sourceId);
        AudioCueWrite write = new AudioCueWrite(
                newCueKey, original.getName(),
                original.getProviderId(), original.getReferenceKind(),
                original.getProviderReference(), original.getCachedTitle(),
                original.getArtistOrOwner(), original.getArtworkUrl(),
                original.getDurationSeconds(), original.getCategory(),
                original.getVolumeHint(), original.getTransitionPreference(),
                original.getNotes(), null);
        validator.validate(write, campaignId);

        AudioCue clone = new AudioCue();
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found: " + campaignId));
        clone.setCampaign(campaign);
        applyWrite(clone, write);
        return repository.save(clone);
    }

    @Transactional(readOnly = true)
    public AudioCueDeletionImpact computeDeletionImpact(UUID id) {
        AudioCue cue = findById(id);
        return dependencyService.computeDeletionImpact(cue);
    }

    public void deleteCue(UUID id, boolean confirmed) {
        AudioCue cue = findById(id);
        if (!confirmed) {
            AudioCueDeletionImpact impact = dependencyService.computeDeletionImpact(cue);
            if (impact.hasDependents()) {
                throw new IllegalArgumentException(
                        "Cue has " + impact.dependencies().size()
                                + " dependent(s); set confirmed=true to proceed");
            }
        }
        repository.delete(cue);
    }

    private void applyWrite(AudioCue cue, AudioCueWrite write) {
        cue.setCueKey(write.cueKey());
        cue.setName(write.name());

        if (write.providerId() != null && !write.providerId().isBlank()) {
            var adapter = AudioProviderRegistry.lookup(write.providerId());
            ParsedAudioReference parsed = adapter.parseReference(write.providerReference());
            cue.setProviderId(write.providerId());
            cue.setReferenceKind(parsed.kind());
            cue.setProviderReference(parsed.id());
        } else {
            cue.setProviderId(null);
            cue.setReferenceKind(write.referenceKind());
            cue.setProviderReference(write.providerReference());
        }

        cue.setCachedTitle(write.cachedTitle());
        cue.setArtistOrOwner(write.artistOrOwner());
        cue.setArtworkUrl(write.artworkUrl());
        cue.setDurationSeconds(write.durationSeconds());
        cue.setCategory(write.category());
        cue.setVolumeHint(write.volumeHint());
        cue.setTransitionPreference(write.transitionPreference());
        cue.setNotes(write.notes());
    }
}
