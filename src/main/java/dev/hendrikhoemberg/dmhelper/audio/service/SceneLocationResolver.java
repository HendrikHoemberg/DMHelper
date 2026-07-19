package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLink;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRole;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocation;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

@Service
public class SceneLocationResolver {

    private final SceneLinkRepository sceneLinkRepository;
    private final WorldLocationRepository worldLocationRepository;

    public SceneLocationResolver(SceneLinkRepository sceneLinkRepository,
                                  WorldLocationRepository worldLocationRepository) {
        this.sceneLinkRepository = sceneLinkRepository;
        this.worldLocationRepository = worldLocationRepository;
    }

    public Optional<AudioCue> resolve(UUID sceneId, UUID campaignId) {
        return sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId).stream()
                .filter(link -> link.getRole() == SceneLinkRole.LOCATION)
                .filter(link -> link.getTargetScope() == SceneLinkTargetScope.PACKAGE)
                .filter(link -> "WORLD_LOCATION".equals(link.getTargetType()))
                .filter(link -> link.getTargetId() != null)
                .sorted(Comparator.comparingInt(SceneLink::getSortOrder)
                        .thenComparing(link -> link.getId(), Comparator.naturalOrder()))
                .map(link -> worldLocationRepository.findById(link.getTargetId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(loc -> loc.getCampaign() != null && campaignId.equals(loc.getCampaign().getId()))
                .map(WorldLocation::getLocationAudioCue)
                .filter(cue -> cue != null)
                .findFirst();
    }

    public Optional<AudioCueSource> sourceFor(UUID sceneId, UUID campaignId, UUID cueId) {
        return sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId).stream()
                .filter(link -> link.getRole() == SceneLinkRole.LOCATION)
                .filter(link -> link.getTargetScope() == SceneLinkTargetScope.PACKAGE)
                .filter(link -> "WORLD_LOCATION".equals(link.getTargetType()))
                .filter(link -> link.getTargetId() != null)
                .sorted(Comparator.comparingInt(SceneLink::getSortOrder)
                        .thenComparing(SceneLink::getId))
                .map(link -> worldLocationRepository.findByIdAndCampaignId(link.getTargetId(), campaignId))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(location -> location.getLocationAudioCue() != null
                        && cueId.equals(location.getLocationAudioCue().getId()))
                .map(location -> new AudioCueSource(AudioCueSource.SourceKind.LOCATION,
                        location.getId(), "Location: " + location.getName()))
                .findFirst();
    }
}
