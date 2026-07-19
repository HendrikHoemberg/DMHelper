package dev.hendrikhoemberg.dmhelper.audio.packagev2;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AudioCueDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AudioCueSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final AudioCueRepository audioCueRepository;

    public AudioCueSectionAdapter(AudioCueRepository audioCueRepository) {
        this.audioCueRepository = audioCueRepository;
    }

    @Override
    public String sectionName() {
        return "AudioCue";
    }

    @Override
    public int order() {
        return 180;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        List<AudioCue> cues = audioCueRepository.findByCampaignIdOrderByNameAsc(context.campaignId());
        List<AudioCueDto> dtos = cues.stream()
                .map(cue -> {
                    String key = context.key(CampaignContentType.AUDIO_CUE, cue.getId(), cue.getName());
                    return new AudioCueDto(
                            key,
                            cue.getName(),
                            cue.getProviderId(),
                            cue.getReferenceKind().name(),
                            cue.getProviderReference(),
                            cue.getCachedTitle(),
                            cue.getArtistOrOwner(),
                            cue.getArtworkUrl(),
                            cue.getDurationSeconds(),
                            cue.getCategory().name(),
                            cue.getVolumeHint(),
                            cue.getTransitionPreference().name(),
                            cue.getNotes()
                    );
                })
                .toList();
        target.audioCues(dtos);
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<AudioCueDto> dtos = source.audioCues();
        if (dtos == null) return;

        var campaign = context.campaign();

        for (AudioCueDto dto : dtos) {
            var cue = new AudioCue();
            cue.setCampaign(campaign);
            cue.setCueKey(dto.key());
            cue.setName(dto.name() != null ? dto.name() : dto.key());
            cue.setProviderId(dto.providerId() != null && !dto.providerId().isBlank() ? dto.providerId() : null);
            if (dto.referenceKind() != null) {
                cue.setReferenceKind(dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind.valueOf(dto.referenceKind()));
            }
            cue.setProviderReference(dto.providerReference() != null ? dto.providerReference() : "");
            cue.setCachedTitle(dto.cachedTitle());
            cue.setArtistOrOwner(dto.artistOrOwner());
            cue.setArtworkUrl(dto.artworkUrl());
            cue.setDurationSeconds(dto.durationSeconds());
            if (dto.category() != null) {
                cue.setCategory(dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory.valueOf(dto.category()));
            } else {
                cue.setCategory(dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory.CUSTOM);
            }
            cue.setVolumeHint(dto.volumeHint());
            if (dto.transitionPreference() != null) {
                cue.setTransitionPreference(dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference.valueOf(dto.transitionPreference()));
            } else {
                cue.setTransitionPreference(dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference.CROSSFADE);
            }
            cue.setNotes(dto.notes());

            audioCueRepository.save(cue);
            context.register(CampaignContentType.AUDIO_CUE, dto.key(), cue, cue.getId());
        }
    }
}
