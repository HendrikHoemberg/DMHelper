package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleDefinition;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleRegistry;
import dev.hendrikhoemberg.dmhelper.session.service.CockpitLayoutPresetService;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService;
import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Controller
public class SessionController {

    private final SessionWorkspaceService workspaces;
    private final AdventureService adventures;
    private final SceneEncounterSeedService encounterSeeder;
    private final CockpitRuntimeModuleViewService moduleViews;
    private final CockpitModuleRegistry cockpitModules;
    private final CockpitLayoutPresetService cockpitPresets;

    @Value("${dmhelper.audio.test-provider:false}")
    private boolean testAudioProvider;

    public SessionController(SessionWorkspaceService workspaces,
                             AdventureService adventures,
                             SceneEncounterSeedService encounterSeeder,
                             CockpitRuntimeModuleViewService moduleViews,
                             CockpitModuleRegistry cockpitModules,
                             CockpitLayoutPresetService cockpitPresets) {
        this.workspaces = workspaces;
        this.adventures = adventures;
        this.encounterSeeder = encounterSeeder;
        this.moduleViews = moduleViews;
        this.cockpitModules = cockpitModules;
        this.cockpitPresets = cockpitPresets;
    }

    @GetMapping("/campaigns/{campaignId}/session")
    public String cockpit(@PathVariable UUID campaignId,
                          @RequestParam(required = false) UUID mapId,
                          Model model) {
        var standardMode = dev.hendrikhoemberg.dmhelper.session.runtime.CockpitModuleMode.STANDARD;
        SessionWorkspaceService.SessionWorkspace workspace = workspaces.load(campaignId, mapId);
        model.addAttribute("workspace", workspace);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("scenePickerGroups", adventures.scenePickerGroups(campaignId));
        model.addAttribute("testAudioProvider", testAudioProvider);
        model.addAttribute("attendeeIds", workspace.session().getStatus()
                == dev.hendrikhoemberg.dmhelper.session.data.CampaignSession.Status.IDLE
                ? workspace.partyMembers().stream().map(member -> member.getId().toString()).toList()
                : workspace.session().getAttendees().stream().map(member -> member.getId().toString()).toList());
        var attendanceMembers = new ArrayList<>(workspace.partyMembers());
        workspace.session().getAttendees().stream()
                .filter(attendee -> attendanceMembers.stream()
                        .noneMatch(member -> member.getId().equals(attendee.getId())))
                .forEach(attendanceMembers::add);
        attendanceMembers.sort(Comparator.comparing(
                dev.hendrikhoemberg.dmhelper.party.data.PartyMember::getCharacterName,
                String.CASE_INSENSITIVE_ORDER));
        model.addAttribute("attendanceMembers", attendanceMembers);
        model.addAttribute("cockpitModules", cockpitModules.all());
        model.addAttribute("cockpitModuleByKey", moduleByKey(cockpitModules));
        model.addAttribute("cockpitModuleStandardMode", standardMode);
        model.addAttribute("initialStoryView", moduleViews.story(campaignId));
        model.addAttribute("initialSessionPlanView", moduleViews.sessionPlan(campaignId));
        model.addAttribute("initialPartyView", moduleViews.party(campaignId));
        model.addAttribute("initialQuickNotesView", moduleViews.quickNotes(campaignId));
        model.addAttribute("cockpitPresets", cockpitPresets.list());
        model.addAttribute("cockpitDefaultPresetKey", "builtin:exploration");
        model.addAttribute("layoutEditing", true);
        addSeedEligibility(campaignId, workspace, model);
        return "session/cockpit";
    }

    @GetMapping("/campaigns/{campaignId}/session/rails/story")
    public String storyRail(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("view", moduleViews.story(campaignId));
        model.addAttribute("mode", dev.hendrikhoemberg.dmhelper.session.runtime.CockpitModuleMode.STANDARD);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("scenePickerGroups", adventures.scenePickerGroups(campaignId));
        return "session/modules/_story :: body";
    }

    @GetMapping("/campaigns/{campaignId}/session/rails/encounter")
    public String encounterRail(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("view", moduleViews.encounter(campaignId));
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("mode", dev.hendrikhoemberg.dmhelper.session.runtime.CockpitModuleMode.STANDARD);
        return "session/_encounter-rail :: encounters";
    }

    private void addSeedEligibility(UUID campaignId,
                                    SessionWorkspaceService.SessionWorkspace workspace,
                                    Model model) {
        boolean eligible = workspace.currentScene() != null
                && workspace.currentScene().getEncounter() == null
                && encounterSeeder.canSeed(campaignId, workspace.currentScene().getId());
        model.addAttribute("canSeedEncounter", eligible);
    }

    private static Map<String, CockpitModuleDefinition> moduleByKey(CockpitModuleRegistry registry) {
        Map<String, CockpitModuleDefinition> byKey = new LinkedHashMap<>();
        for (CockpitModuleDefinition definition : registry.all()) {
            byKey.put(definition.key(), definition);
        }
        return Map.copyOf(byKey);
    }
}
