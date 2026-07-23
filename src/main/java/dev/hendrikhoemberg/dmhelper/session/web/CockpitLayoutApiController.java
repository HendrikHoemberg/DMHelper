package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleDefinition;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleRegistry;
import dev.hendrikhoemberg.dmhelper.session.service.CockpitLayoutPresetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cockpit-layout")
public class CockpitLayoutApiController {
    private final CockpitModuleRegistry modules;
    private final CockpitLayoutPresetService presets;

    public CockpitLayoutApiController(
            CockpitModuleRegistry modules,
            CockpitLayoutPresetService presets) {
        this.modules = modules;
        this.presets = presets;
    }

    @GetMapping("/modules")
    List<CockpitModuleDefinition> modules() {
        return modules.all();
    }

    @GetMapping("/presets")
    List<CockpitLayoutPresetService.PresetDto> presets() {
        return presets.list();
    }

    @PostMapping("/presets")
    ResponseEntity<CockpitLayoutPresetService.PresetDto> create(
            @RequestBody CockpitLayoutPresetService.SavePresetRequest request) {
        var created = presets.create(request);
        return ResponseEntity.created(URI.create(
                "/api/v1/cockpit-layout/presets/" + created.id())).body(created);
    }

    @PutMapping("/presets/{id}")
    CockpitLayoutPresetService.PresetDto update(
            @PathVariable UUID id,
            @RequestBody CockpitLayoutPresetService.UpdatePresetRequest request) {
        return presets.update(id, request);
    }

    @DeleteMapping("/presets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        presets.delete(id);
    }
}
