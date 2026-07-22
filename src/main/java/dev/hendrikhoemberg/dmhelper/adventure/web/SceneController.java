package dev.hendrikhoemberg.dmhelper.adventure.web;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneStructuredContentService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneTransitionService;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
public class SceneController {

    private final AdventureService adventureService;
    private final CampaignRepository campaignRepository;
    private final GameMapRepository gameMapRepository;
    private final EncounterRepository encounterRepository;
    private final StatBlockRepository statBlockRepository;
    private final HandoutRepository handoutRepository;
    private final MarkdownUtil markdownUtil;
    private final SceneStructuredContentService structuredService;
    private final SceneTransitionService transitionService;
    private final TrapRepository trapRepository;
    private final HazardRepository hazardRepository;
    private final AudioCueRepository audioCueRepository;
    private final SceneEncounterSeedService encounterSeeder;

    public SceneController(AdventureService adventureService,
                           CampaignRepository campaignRepository,
                           GameMapRepository gameMapRepository,
                           EncounterRepository encounterRepository,
                           StatBlockRepository statBlockRepository,
                           HandoutRepository handoutRepository,
                           MarkdownUtil markdownUtil,
                           SceneStructuredContentService structuredService,
                           SceneTransitionService transitionService,
                           TrapRepository trapRepository,
                            HazardRepository hazardRepository,
                            AudioCueRepository audioCueRepository,
                            SceneEncounterSeedService encounterSeeder) {
        this.adventureService = adventureService;
        this.campaignRepository = campaignRepository;
        this.gameMapRepository = gameMapRepository;
        this.encounterRepository = encounterRepository;
        this.statBlockRepository = statBlockRepository;
        this.handoutRepository = handoutRepository;
        this.markdownUtil = markdownUtil;
        this.trapRepository = trapRepository;
        this.hazardRepository = hazardRepository;
        this.audioCueRepository = audioCueRepository;
        this.structuredService = structuredService;
        this.transitionService = transitionService;
        this.encounterSeeder = encounterSeeder;
    }

    @GetMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}")
    public String sceneDetail(@PathVariable UUID campaignId,
                              @PathVariable UUID adventureId,
                              @PathVariable UUID id,
                              Model model) {
        AdventureService.SceneDetailView view = adventureService.findSceneDetailView(id);
        Scene scene = view.scene();
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("scene", scene);
        model.addAttribute("canSeedEncounter", encounterSeeder.canSeed(campaignId, id));
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("chapters", adventureService.findChaptersByAdventure(adventureId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("renderedBody", markdownUtil.toHtml(scene.getBody()));
        model.addAttribute("maps", gameMapRepository.findByCampaignIdOrderBySortOrderAsc(campaignId));
        model.addAttribute("encounters", encounterRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("statBlocks", statBlockRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("handouts", handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId));
        model.addAttribute("visibleTraps", trapRepository.findVisibleByCampaignId(campaignId));
        model.addAttribute("visibleHazards", hazardRepository.findVisibleByCampaignId(campaignId));
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("sectionThreatCards", view.sectionThreatCards());
        adventureService.getCurrentScene(campaignId).ifPresent(s -> model.addAttribute("currentScene", s));
        return "adventure/scene-detail";
    }

    @GetMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/edit")
    public String editForm(@PathVariable UUID campaignId,
                           @PathVariable UUID adventureId,
                           @PathVariable UUID id,
                           Model model) {
        Scene scene = adventureService.findSceneById(id);
        model.addAttribute("scene", scene);
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("maps", gameMapRepository.findByCampaignIdOrderBySortOrderAsc(campaignId));
        model.addAttribute("encounters", encounterRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("statBlocks", statBlockRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("handouts", handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId));
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(campaignId));
        return "adventure/_scene-form :: form";
    }

    @PutMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}")
    public String updateScene(@PathVariable UUID campaignId,
                              @PathVariable UUID adventureId,
                              @PathVariable UUID id,
                              @RequestParam String title,
                              @RequestParam(required = false) String sceneKey,
                              @RequestParam(required = false) String body,
                              @RequestParam(required = false) String summary,
                              @RequestParam(required = false) String sourceLocator,
                              @RequestParam(required = false) String tags,
                              @RequestParam(required = false) String mapRegionKey,
                              Model model) {
        Scene scene = adventureService.updateScene(id, title, sceneKey, body);
        try {
            structuredService.updateMetadata(campaignId, id,
                    new SceneStructuredContentService.SceneMetadataCommand(
                            summary, sourceLocator, tags, mapRegionKey));
            scene = adventureService.findSceneById(id);
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("scene", scene);
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("renderedBody", markdownUtil.toHtml(scene.getBody()));
        return "adventure/scene-detail :: sceneBody";
    }

    @DeleteMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}")
    public ResponseEntity<Void> deleteScene(@PathVariable UUID campaignId,
                                            @PathVariable UUID adventureId,
                                            @PathVariable UUID id) {
        adventureService.deleteScene(id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/adventures/" + adventureId)
                .build();
    }

    @PutMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/move")
    public ResponseEntity<Void> moveScene(@PathVariable UUID campaignId,
                                          @PathVariable UUID adventureId,
                                          @PathVariable UUID id,
                                          @RequestParam int direction) {
        adventureService.moveScene(id, direction);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/adventures/" + adventureId)
                .build();
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/move-to-chapter")
    public ResponseEntity<Void> moveSceneToChapter(@PathVariable UUID campaignId,
                                                   @PathVariable UUID adventureId,
                                                   @PathVariable UUID id,
                                                   @RequestParam UUID chapterId) {
        adventureService.moveSceneToChapter(id, chapterId);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/campaigns/" + campaignId + "/adventures/" + adventureId)
                .build();
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/link-map")
    public String linkMap(@PathVariable UUID campaignId,
                          @PathVariable UUID adventureId,
                          @PathVariable UUID id,
                          @RequestParam UUID mapId,
                          @RequestParam(required = false) Integer pinX,
                          @RequestParam(required = false) Integer pinY,
                          Model model) {
        adventureService.linkMap(id, mapId, pinX, pinY);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/unlink-map")
    public String unlinkMap(@PathVariable UUID campaignId,
                            @PathVariable UUID adventureId,
                            @PathVariable UUID id,
                            Model model) {
        adventureService.unlinkMap(id);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/link-encounter")
    public String linkEncounter(@PathVariable UUID campaignId,
                                @PathVariable UUID adventureId,
                                @PathVariable UUID id,
                                @RequestParam UUID encounterId,
                                Model model) {
        adventureService.linkEncounter(id, encounterId);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/unlink-encounter")
    public String unlinkEncounter(@PathVariable UUID campaignId,
                                  @PathVariable UUID adventureId,
                                  @PathVariable UUID id,
                                  Model model) {
        adventureService.unlinkEncounter(id);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    /**
     * The app already knows every combatant in a scene, its count and its statblock, so the
     * DM should not have to retype them when initiative starts. Returns the action rail so the
     * new Linked Encounter block and the seed report swap in together.
     */
    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/seed-encounter")
    public String seedEncounter(@PathVariable UUID campaignId,
                                @PathVariable UUID adventureId,
                                @PathVariable UUID id,
                                Model model) {
        model.addAttribute("seedResult", encounterSeeder.seedFromScene(campaignId, id));
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/add-statblock")
    public String addStatBlock(@PathVariable UUID campaignId,
                               @PathVariable UUID adventureId,
                               @PathVariable UUID id,
                               @RequestParam UUID statBlockId,
                               Model model) {
        adventureService.addStatBlock(id, statBlockId);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/remove-statblock")
    public String removeStatBlock(@PathVariable UUID campaignId,
                                  @PathVariable UUID adventureId,
                                  @PathVariable UUID id,
                                  @RequestParam UUID statBlockId,
                                  Model model) {
        adventureService.removeStatBlock(id, statBlockId);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/add-handout")
    public String addHandout(@PathVariable UUID campaignId,
                             @PathVariable UUID adventureId,
                             @PathVariable UUID id,
                             @RequestParam UUID handoutId,
                             Model model) {
        adventureService.addHandout(id, handoutId);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/scenes/{id}/remove-handout")
    public String removeHandout(@PathVariable UUID campaignId,
                                @PathVariable UUID adventureId,
                                @PathVariable UUID id,
                                @RequestParam UUID handoutId,
                                Model model) {
        adventureService.removeHandout(id, handoutId);
        return loadActionRail(campaignId, adventureId, id, model);
    }

    @PutMapping("/scenes/{id}/status")
    public String updateSceneStatus(@PathVariable UUID id,
                                    @RequestParam SceneStatus status,
                                    Model model) {
        Scene scene = adventureService.setStatus(id, status);
        model.addAttribute("scene", scene);
        return "adventure/_action-rail :: statusBadge";
    }

    @PostMapping("/campaigns/{campaignId}/current-scene")
    public String setCurrentScene(@PathVariable UUID campaignId,
                                  @RequestParam(defaultValue = "") String sceneId,
                                  Model model) {
        if (sceneId.isBlank()) {
            adventureService.clearCurrentScene(campaignId);
        } else {
            try {
                Scene s = adventureService.setCurrentScene(campaignId, UUID.fromString(sceneId));
                model.addAttribute("scene", s);
            } catch (IllegalArgumentException e) {
                // invalid UUID — reload without setting current scene
            }
        }
        model.addAttribute("campaignId", campaignId);
        adventureService.getCurrentScene(campaignId).ifPresent(s -> model.addAttribute("currentScene", s));
        return "adventure/_scene-panel :: scenePanel";
    }

    @PutMapping("/campaigns/{campaignId}/current-scene/step")
    public String stepCurrentScene(@PathVariable UUID campaignId,
                                   @RequestParam int direction) {
        return adventureService.stepCurrentScene(campaignId, direction)
                .map(s -> "redirect:/campaigns/" + campaignId + "/adventures/"
                        + s.getChapter().getAdventure().getId() + "/scenes/" + s.getId())
                .orElse("redirect:/campaigns/" + campaignId + "/adventures");
    }

    // ---- Structured content: metadata ----

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/metadata")
    public String updateMetadata(@PathVariable UUID campaignId,
                                 @PathVariable UUID adventureId,
                                 @PathVariable UUID chapterId,
                                 @PathVariable UUID sceneId,
                                 @RequestParam(required = false) String summary,
                                 @RequestParam(required = false) String sourceLocator,
                                 @RequestParam(required = false) String tags,
                                 @RequestParam(required = false) String mapRegionKey,
                                 Model model) {
        try {
            structuredService.updateMetadata(campaignId, sceneId,
                    new SceneStructuredContentService.SceneMetadataCommand(
                            summary, sourceLocator, tags, mapRegionKey));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    // ---- Sections ----

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/sections")
    public String addSection(@PathVariable UUID campaignId,
                             @PathVariable UUID adventureId,
                             @PathVariable UUID chapterId,
                             @PathVariable UUID sceneId,
                             @RequestParam SceneSectionKind kind,
                             @RequestParam String label,
                             @RequestParam String body,
                             @RequestParam(required = false) String sourceLocator,
                             @RequestParam(defaultValue = "0") int sortOrder,
                             @RequestParam(required = false) UUID threatId,
                             Model model) {
        try {
            ThreatKind threatKind = resolveSectionThreatKind(kind, threatId);
            structuredService.addSection(campaignId, sceneId,
                    new SceneStructuredContentService.SceneSectionCommand(
                            kind, label, body, sourceLocator, sortOrder, threatKind, threatId));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/sections/{sectionId}")
    public String updateSection(@PathVariable UUID campaignId,
                                @PathVariable UUID adventureId,
                                @PathVariable UUID chapterId,
                                @PathVariable UUID sceneId,
                                @PathVariable UUID sectionId,
                                @RequestParam SceneSectionKind kind,
                                @RequestParam String label,
                                @RequestParam String body,
                                @RequestParam(required = false) String sourceLocator,
                                @RequestParam(defaultValue = "0") int sortOrder,
                                @RequestParam(required = false) UUID threatId,
                                Model model) {
        try {
            ThreatKind threatKind = resolveSectionThreatKind(kind, threatId);
            structuredService.updateSection(campaignId, sceneId, sectionId,
                    new SceneStructuredContentService.SceneSectionCommand(
                            kind, label, body, sourceLocator, sortOrder, threatKind, threatId));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    private static ThreatKind resolveSectionThreatKind(SceneSectionKind kind, UUID threatId) {
        if (threatId == null) {
            return null;
        }
        return switch (kind) {
            case TRAP -> ThreatKind.TRAP;
            case HAZARD -> ThreatKind.HAZARD;
            default -> null;
        };
    }

    @DeleteMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/sections/{sectionId}")
    public String deleteSection(@PathVariable UUID campaignId,
                                @PathVariable UUID adventureId,
                                @PathVariable UUID chapterId,
                                @PathVariable UUID sceneId,
                                @PathVariable UUID sectionId,
                                Model model) {
        structuredService.deleteSection(campaignId, sceneId, sectionId);
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    // ---- Checks ----

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/checks")
    public String addCheck(@PathVariable UUID campaignId,
                           @PathVariable UUID adventureId,
                           @PathVariable UUID chapterId,
                           @PathVariable UUID sceneId,
                           @RequestParam(required = false) String label,
                           @RequestParam(required = false) String ability,
                           @RequestParam(required = false) String skill,
                           @RequestParam(required = false) Integer dc,
                           @RequestParam(required = false) SceneCheckVisibility visibility,
                           @RequestParam(required = false) String success,
                           @RequestParam(required = false) String failure,
                           @RequestParam(required = false) String partial,
                           @RequestParam(required = false) String sourceLocator,
                           @RequestParam(defaultValue = "0") int sortOrder,
                           Model model) {
        try {
            structuredService.addCheck(campaignId, sceneId,
                    new SceneStructuredContentService.SceneCheckCommand(
                            label, ability, skill, dc, visibility,
                            success, failure, partial,
                            null, null, null, sourceLocator, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/checks/{checkId}")
    public String updateCheck(@PathVariable UUID campaignId,
                              @PathVariable UUID adventureId,
                              @PathVariable UUID chapterId,
                              @PathVariable UUID sceneId,
                              @PathVariable UUID checkId,
                              @RequestParam(required = false) String label,
                              @RequestParam(required = false) String ability,
                              @RequestParam(required = false) String skill,
                              @RequestParam(required = false) Integer dc,
                              @RequestParam(required = false) SceneCheckVisibility visibility,
                              @RequestParam(required = false) String success,
                              @RequestParam(required = false) String failure,
                              @RequestParam(required = false) String partial,
                              @RequestParam(required = false) String sourceLocator,
                              @RequestParam(defaultValue = "0") int sortOrder,
                              Model model) {
        try {
            structuredService.updateCheck(campaignId, sceneId, checkId,
                    new SceneStructuredContentService.SceneCheckCommand(
                            label, ability, skill, dc, visibility,
                            success, failure, partial,
                            null, null, null, sourceLocator, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    @DeleteMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/checks/{checkId}")
    public String deleteCheck(@PathVariable UUID campaignId,
                              @PathVariable UUID adventureId,
                              @PathVariable UUID chapterId,
                              @PathVariable UUID sceneId,
                              @PathVariable UUID checkId,
                              Model model) {
        structuredService.deleteCheck(campaignId, sceneId, checkId);
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    // ---- Participants ----

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/participants")
    public String addParticipant(@PathVariable UUID campaignId,
                                 @PathVariable UUID adventureId,
                                 @PathVariable UUID chapterId,
                                 @PathVariable UUID sceneId,
                                 @RequestParam(required = false) String displayName,
                                 @RequestParam(defaultValue = "1") int quantity,
                                 @RequestParam(required = false) SceneParticipantDisposition disposition,
                                 @RequestParam(required = false) String placementHint,
                                 @RequestParam(required = false) String sourceLocator,
                                 @RequestParam(defaultValue = "0") int sortOrder,
                                 Model model) {
        try {
            structuredService.addParticipant(campaignId, sceneId,
                    new SceneStructuredContentService.SceneParticipantCommand(
                            displayName, quantity, disposition, placementHint,
                            null, null, sourceLocator, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/participants/{participantId}")
    public String updateParticipant(@PathVariable UUID campaignId,
                                    @PathVariable UUID adventureId,
                                    @PathVariable UUID chapterId,
                                    @PathVariable UUID sceneId,
                                    @PathVariable UUID participantId,
                                    @RequestParam(required = false) String displayName,
                                    @RequestParam(defaultValue = "1") int quantity,
                                    @RequestParam(required = false) SceneParticipantDisposition disposition,
                                    @RequestParam(required = false) String placementHint,
                                    @RequestParam(required = false) String sourceLocator,
                                    @RequestParam(defaultValue = "0") int sortOrder,
                                    Model model) {
        try {
            structuredService.updateParticipant(campaignId, sceneId, participantId,
                    new SceneStructuredContentService.SceneParticipantCommand(
                            displayName, quantity, disposition, placementHint,
                            null, null, sourceLocator, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    @DeleteMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/participants/{participantId}")
    public String deleteParticipant(@PathVariable UUID campaignId,
                                    @PathVariable UUID adventureId,
                                    @PathVariable UUID chapterId,
                                    @PathVariable UUID sceneId,
                                    @PathVariable UUID participantId,
                                    Model model) {
        structuredService.deleteParticipant(campaignId, sceneId, participantId);
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    // ---- Links ----

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/links")
    public String addLink(@PathVariable UUID campaignId,
                          @PathVariable UUID adventureId,
                          @PathVariable UUID chapterId,
                          @PathVariable UUID sceneId,
                          @RequestParam SceneLinkRole role,
                          @RequestParam SceneLinkTargetScope targetScope,
                          @RequestParam String targetType,
                          @RequestParam(required = false) UUID targetId,
                          @RequestParam(required = false) String catalogRuleset,
                          @RequestParam(required = false) String catalogSourceKey,
                          @RequestParam(required = false) String displayText,
                          @RequestParam(required = false) String condition,
                          @RequestParam(defaultValue = "0") int sortOrder,
                          Model model) {
        try {
            structuredService.addLink(campaignId, sceneId,
                    new SceneStructuredContentService.SceneLinkCommand(
                            role, targetScope, targetType, targetId,
                            catalogRuleset, catalogSourceKey, displayText, condition, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/links/{linkId}")
    public String updateLink(@PathVariable UUID campaignId,
                             @PathVariable UUID adventureId,
                             @PathVariable UUID chapterId,
                             @PathVariable UUID sceneId,
                             @PathVariable UUID linkId,
                             @RequestParam SceneLinkRole role,
                             @RequestParam SceneLinkTargetScope targetScope,
                             @RequestParam String targetType,
                             @RequestParam UUID targetId,
                             @RequestParam(required = false) String displayText,
                             @RequestParam(required = false) String condition,
                             @RequestParam(defaultValue = "0") int sortOrder,
                             Model model) {
        try {
            structuredService.updateLink(campaignId, sceneId, linkId,
                    new SceneStructuredContentService.SceneLinkCommand(
                            role, targetScope, targetType, targetId,
                            null, null, displayText, condition, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    @DeleteMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/links/{linkId}")
    public String deleteLink(@PathVariable UUID campaignId,
                             @PathVariable UUID adventureId,
                             @PathVariable UUID chapterId,
                             @PathVariable UUID sceneId,
                             @PathVariable UUID linkId,
                             Model model) {
        structuredService.deleteLink(campaignId, sceneId, linkId);
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    // ---- Transitions ----

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/transitions")
    public String addTransition(@PathVariable UUID campaignId,
                                @PathVariable UUID adventureId,
                                @PathVariable UUID chapterId,
                                @PathVariable UUID sceneId,
                                @RequestParam SceneTransitionKind kind,
                                @RequestParam(required = false) String label,
                                @RequestParam(required = false) UUID targetSceneId,
                                @RequestParam(required = false) String externalDestination,
                                @RequestParam(required = false) String condition,
                                @RequestParam(required = false) String dmNote,
                                @RequestParam(required = false) String sourceLocator,
                                @RequestParam(defaultValue = "0") int sortOrder,
                                Model model) {
        try {
            structuredService.addTransition(campaignId, sceneId,
                    new SceneStructuredContentService.SceneTransitionCommand(
                            kind, label, targetSceneId, externalDestination,
                            condition, dmNote, sourceLocator, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/transitions/{transitionId}")
    public String updateTransition(@PathVariable UUID campaignId,
                                   @PathVariable UUID adventureId,
                                   @PathVariable UUID chapterId,
                                   @PathVariable UUID sceneId,
                                   @PathVariable UUID transitionId,
                                   @RequestParam SceneTransitionKind kind,
                                   @RequestParam(required = false) String label,
                                   @RequestParam(required = false) UUID targetSceneId,
                                   @RequestParam(required = false) String externalDestination,
                                   @RequestParam(required = false) String condition,
                                   @RequestParam(required = false) String dmNote,
                                   @RequestParam(required = false) String sourceLocator,
                                   @RequestParam(defaultValue = "0") int sortOrder,
                                   Model model) {
        try {
            structuredService.updateTransition(campaignId, sceneId, transitionId,
                    new SceneStructuredContentService.SceneTransitionCommand(
                            kind, label, targetSceneId, externalDestination,
                            condition, dmNote, sourceLocator, sortOrder));
        } catch (IllegalArgumentException | NotFoundException e) {
            model.addAttribute("error", e.getMessage());
        }
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    @DeleteMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/transitions/{transitionId}")
    public String deleteTransition(@PathVariable UUID campaignId,
                                   @PathVariable UUID adventureId,
                                   @PathVariable UUID chapterId,
                                   @PathVariable UUID sceneId,
                                   @PathVariable UUID transitionId,
                                   Model model) {
        structuredService.deleteTransition(campaignId, sceneId, transitionId);
        return loadActionRail(campaignId, adventureId, sceneId, model);
    }

    // ---- Follow transition ----

    @PostMapping("/campaigns/{campaignId}/adventures/{adventureId}/chapters/{chapterId}/scenes/{sceneId}/transitions/{transitionId}/follow")
    public String followTransition(@PathVariable UUID campaignId,
                                   @PathVariable UUID adventureId,
                                   @PathVariable UUID chapterId,
                                   @PathVariable UUID sceneId,
                                   @PathVariable UUID transitionId,
                                   Model model) {
        Scene target = adventureService.followTransition(campaignId, transitionId);
        return "redirect:/campaigns/" + campaignId + "/adventures/"
                + target.getChapter().getAdventure().getId() + "/scenes/" + target.getId();
    }

    private String loadActionRail(UUID campaignId, UUID adventureId, UUID sceneId, Model model) {
        AdventureService.SceneDetailView view = adventureService.findSceneDetailView(sceneId);
        Scene scene = view.scene();
        model.addAttribute("scene", scene);
        model.addAttribute("canSeedEncounter", encounterSeeder.canSeed(campaignId, sceneId));
        model.addAttribute("adventure", adventureService.findAdventureById(adventureId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("maps", gameMapRepository.findByCampaignIdOrderBySortOrderAsc(campaignId));
        model.addAttribute("encounters", encounterRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("statBlocks", statBlockRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("handouts", handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId));
        model.addAttribute("visibleTraps", trapRepository.findVisibleByCampaignId(campaignId));
        model.addAttribute("visibleHazards", hazardRepository.findVisibleByCampaignId(campaignId));
        model.addAttribute("audioCues", audioCueRepository.findByCampaignIdOrderByNameAsc(campaignId));
        model.addAttribute("sectionThreatCards", view.sectionThreatCards());
        return "adventure/_action-rail :: actionRail";
    }
}
