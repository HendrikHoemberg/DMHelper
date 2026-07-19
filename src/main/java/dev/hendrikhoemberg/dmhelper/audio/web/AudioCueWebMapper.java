package dev.hendrikhoemberg.dmhelper.audio.web;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;

import java.util.UUID;

public final class AudioCueWebMapper {

    private AudioCueWebMapper() {}

    public static AudioCueResponse fromCue(AudioCue cue) {
        UUID campaignId = cue.getCampaign() == null ? null : cue.getCampaign().getId();
        return new AudioCueResponse(
                cue.getId(), campaignId,
                cue.getCueKey(), cue.getName(),
                cue.getProviderId(), cue.getReferenceKind(),
                cue.getProviderReference(), cue.getCachedTitle(),
                cue.getArtistOrOwner(), cue.getArtworkUrl(),
                cue.getDurationSeconds(), cue.getCategory(),
                cue.getVolumeHint(), cue.getTransitionPreference(),
                cue.getNotes(), cue.getCreatedAt(), cue.getUpdatedAt());
    }
}
