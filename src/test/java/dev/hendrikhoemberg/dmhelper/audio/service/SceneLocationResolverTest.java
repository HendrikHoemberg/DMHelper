package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLink;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRole;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocation;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SceneLocationResolverTest {

    @Mock private SceneLinkRepository sceneLinkRepository;
    @Mock private WorldLocationRepository worldLocationRepository;

    @InjectMocks private SceneLocationResolver resolver;

    private final UUID campaignId = UUID.randomUUID();
    private final UUID sceneId = UUID.randomUUID();

    @Test
    void resolvesLocationCueFromSceneLink() {
        Campaign campaign = campaign(campaignId);
        AudioCue cue = audioCue(campaign);
        WorldLocation location = worldLocation(campaign, cue);

        SceneLink link = sceneLink(sceneId, SceneLinkRole.LOCATION, SceneLinkTargetScope.PACKAGE,
                "WORLD_LOCATION", location.getId(), 0);

        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId)).thenReturn(List.of(link));
        when(worldLocationRepository.findById(location.getId())).thenReturn(Optional.of(location));

        Optional<AudioCue> result = resolver.resolve(sceneId, campaignId);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(cue.getId());
    }

    @Test
    void ignoresNonLocationRole() {
        Campaign campaign = campaign(campaignId);
        AudioCue cue = audioCue(campaign);
        WorldLocation location = worldLocation(campaign, cue);

        SceneLink link = sceneLink(sceneId, SceneLinkRole.REFERENCE, SceneLinkTargetScope.PACKAGE,
                "WORLD_LOCATION", location.getId(), 0);

        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId)).thenReturn(List.of(link));

        Optional<AudioCue> result = resolver.resolve(sceneId, campaignId);

        assertThat(result).isEmpty();
    }

    @Test
    void ignoresCatalogScopedLink() {
        Campaign campaign = campaign(campaignId);
        AudioCue cue = audioCue(campaign);
        WorldLocation location = worldLocation(campaign, cue);

        SceneLink link = sceneLink(sceneId, SceneLinkRole.LOCATION, SceneLinkTargetScope.CATALOG,
                "WORLD_LOCATION", null, 0);

        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId)).thenReturn(List.of(link));

        Optional<AudioCue> result = resolver.resolve(sceneId, campaignId);

        assertThat(result).isEmpty();
    }

    @Test
    void ignoresNonWorldLocationTargetType() {
        Campaign campaign = campaign(campaignId);
        AudioCue cue = audioCue(campaign);
        WorldLocation location = worldLocation(campaign, cue);

        SceneLink link = sceneLink(sceneId, SceneLinkRole.LOCATION, SceneLinkTargetScope.PACKAGE,
                "NPC", location.getId(), 0);

        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId)).thenReturn(List.of(link));

        Optional<AudioCue> result = resolver.resolve(sceneId, campaignId);

        assertThat(result).isEmpty();
    }

    @Test
    void skipsLinkWhoseTargetDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        SceneLink link = sceneLink(sceneId, SceneLinkRole.LOCATION, SceneLinkTargetScope.PACKAGE,
                "WORLD_LOCATION", missingId, 0);

        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId)).thenReturn(List.of(link));
        when(worldLocationRepository.findById(missingId)).thenReturn(Optional.empty());

        Optional<AudioCue> result = resolver.resolve(sceneId, campaignId);

        assertThat(result).isEmpty();
    }

    @Test
    void skipsLocationWithoutAudioCue() {
        Campaign campaign = campaign(campaignId);
        WorldLocation location = worldLocation(campaign, null);

        SceneLink link = sceneLink(sceneId, SceneLinkRole.LOCATION, SceneLinkTargetScope.PACKAGE,
                "WORLD_LOCATION", location.getId(), 0);

        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId)).thenReturn(List.of(link));
        when(worldLocationRepository.findById(location.getId())).thenReturn(Optional.of(location));

        Optional<AudioCue> result = resolver.resolve(sceneId, campaignId);

        assertThat(result).isEmpty();
    }

    @Test
    void skipsLocationFromDifferentCampaign() {
        Campaign otherCampaign = campaign(UUID.randomUUID());
        AudioCue cue = audioCue(otherCampaign);
        WorldLocation location = worldLocation(otherCampaign, cue);

        SceneLink link = sceneLink(sceneId, SceneLinkRole.LOCATION, SceneLinkTargetScope.PACKAGE,
                "WORLD_LOCATION", location.getId(), 0);

        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId)).thenReturn(List.of(link));
        when(worldLocationRepository.findById(location.getId())).thenReturn(Optional.of(location));

        Optional<AudioCue> result = resolver.resolve(sceneId, campaignId);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsFirstBySortOrderThenUuid() {
        Campaign campaign = campaign(campaignId);
        AudioCue cueLow = audioCue(campaign);
        AudioCue cueHigh = audioCue(campaign);

        WorldLocation locLow = worldLocation(campaign, cueLow);
        WorldLocation locHigh = worldLocation(campaign, cueHigh);

        SceneLink linkLow = sceneLink(sceneId, SceneLinkRole.LOCATION, SceneLinkTargetScope.PACKAGE,
                "WORLD_LOCATION", locLow.getId(), 0);
        SceneLink linkHigh = sceneLink(sceneId, SceneLinkRole.LOCATION, SceneLinkTargetScope.PACKAGE,
                "WORLD_LOCATION", locHigh.getId(), 1);

        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId)).thenReturn(List.of(linkLow, linkHigh));
        when(worldLocationRepository.findById(locLow.getId())).thenReturn(Optional.of(locLow));

        Optional<AudioCue> result = resolver.resolve(sceneId, campaignId);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(cueLow.getId());
    }

    @Test
    void returnsEmptyForSceneWithNoLocationLinks() {
        when(sceneLinkRepository.findBySceneIdOrderBySortOrderAsc(sceneId)).thenReturn(List.of());

        Optional<AudioCue> result = resolver.resolve(sceneId, campaignId);

        assertThat(result).isEmpty();
    }

    private static SceneLink sceneLink(UUID sceneId, SceneLinkRole role, SceneLinkTargetScope scope,
                                        String targetType, UUID targetId, int sortOrder) {
        SceneLink link = new SceneLink();
        link.setId(UUID.randomUUID());
        link.setRole(role);
        link.setTargetScope(scope);
        link.setTargetType(targetType);
        link.setTargetId(targetId);
        link.setSortOrder(sortOrder);
        return link;
    }

    private static Campaign campaign(UUID id) {
        Campaign c = new Campaign();
        c.setId(id);
        c.setName("Test");
        return c;
    }

    private static AudioCue audioCue(Campaign campaign) {
        AudioCue cue = new AudioCue();
        cue.setId(UUID.randomUUID());
        cue.setCampaign(campaign);
        cue.setName("Test Cue");
        return cue;
    }

    private static WorldLocation worldLocation(Campaign campaign, AudioCue cue) {
        WorldLocation loc = new WorldLocation();
        loc.setId(UUID.randomUUID());
        loc.setCampaign(campaign);
        loc.setName("Location");
        loc.setLocationAudioCue(cue);
        return loc;
    }
}
