package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleDefinition;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleRegistry;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitModuleMode;
import dev.hendrikhoemberg.dmhelper.session.runtime.CockpitRuntimeModuleViewService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/session/modules")
public class CockpitRuntimeModuleController {

    private final CockpitRuntimeModuleViewService views;
    private final CockpitModuleRegistry registry;

    public CockpitRuntimeModuleController(CockpitRuntimeModuleViewService views,
                                          CockpitModuleRegistry registry) {
        this.views = views;
        this.registry = registry;
    }

    @GetMapping("/story")
    public String story(@PathVariable UUID campaignId,
                        @RequestParam(defaultValue = "STANDARD") CockpitModuleMode mode,
                        Model model) {
        CockpitModuleDefinition def = requireModule("story");
        requireMode(def, mode);
        model.addAttribute("view", views.story(campaignId));
        model.addAttribute("mode", mode);
        return "session/modules/_story :: body";
    }

    @GetMapping("/map")
    public String map(@PathVariable UUID campaignId,
                      @RequestParam(required = false) UUID mapId,
                      @RequestParam(defaultValue = "STANDARD") CockpitModuleMode mode,
                      Model model) {
        CockpitModuleDefinition def = requireModule("map");
        requireMode(def, mode);
        model.addAttribute("view", views.map(campaignId, mapId));
        model.addAttribute("mode", mode);
        return "session/modules/_map :: body";
    }

    @GetMapping("/encounter")
    public String encounter(@PathVariable UUID campaignId,
                            @RequestParam(defaultValue = "STANDARD") CockpitModuleMode mode,
                            Model model) {
        CockpitModuleDefinition def = requireModule("encounter");
        requireMode(def, mode);
        model.addAttribute("view", views.encounter(campaignId));
        model.addAttribute("mode", mode);
        return "session/modules/_encounter :: body";
    }

    @GetMapping("/session-plan")
    public String sessionPlan(@PathVariable UUID campaignId,
                              @RequestParam(defaultValue = "STANDARD") CockpitModuleMode mode,
                              Model model) {
        CockpitModuleDefinition def = requireModule("session-plan");
        requireMode(def, mode);
        model.addAttribute("view", views.sessionPlan(campaignId));
        model.addAttribute("mode", mode);
        return "session/modules/_session-plan :: body";
    }

    @GetMapping("/party")
    public String party(@PathVariable UUID campaignId,
                        @RequestParam(defaultValue = "STANDARD") CockpitModuleMode mode,
                        Model model) {
        CockpitModuleDefinition def = requireModule("party");
        requireMode(def, mode);
        model.addAttribute("view", views.party(campaignId));
        model.addAttribute("mode", mode);
        return "session/modules/_party :: body";
    }

    @GetMapping("/quick-notes")
    public String quickNotes(@PathVariable UUID campaignId,
                             @RequestParam(defaultValue = "STANDARD") CockpitModuleMode mode,
                             Model model) {
        CockpitModuleDefinition def = requireModule("quick-notes");
        requireMode(def, mode);
        model.addAttribute("view", views.quickNotes(campaignId));
        model.addAttribute("mode", mode);
        return "session/modules/_quick-notes :: body";
    }

    @GetMapping("/presentation")
    public String presentation(@PathVariable UUID campaignId,
                               @RequestParam(defaultValue = "STANDARD") CockpitModuleMode mode,
                               Model model) {
        CockpitModuleDefinition def = requireModule("presentation");
        requireMode(def, mode);
        model.addAttribute("view", views.presentation(campaignId));
        model.addAttribute("mode", mode);
        return "session/modules/_presentation :: body";
    }

    @GetMapping("/reference")
    public String reference(@PathVariable UUID campaignId,
                            @RequestParam(defaultValue = "STANDARD") CockpitModuleMode mode,
                            Model model) {
        CockpitModuleDefinition def = requireModule("reference");
        requireMode(def, mode);
        model.addAttribute("view", views.reference(campaignId));
        model.addAttribute("mode", mode);
        return "session/modules/_reference :: body";
    }

    @GetMapping("/audio")
    public String audio(@PathVariable UUID campaignId,
                        @RequestParam(defaultValue = "STANDARD") CockpitModuleMode mode,
                        Model model) {
        CockpitModuleDefinition def = requireModule("audio");
        requireMode(def, mode);
        model.addAttribute("view", views.audio(campaignId));
        model.addAttribute("mode", mode);
        return "session/modules/_audio :: body";
    }

    @GetMapping("/session-log")
    public String sessionLog(@PathVariable UUID campaignId,
                             @RequestParam(defaultValue = "STANDARD") CockpitModuleMode mode,
                             Model model) {
        CockpitModuleDefinition def = requireModule("session-log");
        requireMode(def, mode);
        model.addAttribute("view", views.sessionLog(campaignId));
        model.addAttribute("mode", mode);
        return "session/modules/_session-log :: body";
    }

    private CockpitModuleDefinition requireModule(String key) {
        try {
            return registry.require(key);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private void requireMode(CockpitModuleDefinition def, CockpitModuleMode mode) {
        if (mode == CockpitModuleMode.FOCUSED && !def.focusSupported()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Module " + def.key() + " does not support FOCUSED mode");
        }
        if (mode == CockpitModuleMode.COMPACT && !def.compactSupported()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Module " + def.key() + " does not support COMPACT mode");
        }
    }
}
