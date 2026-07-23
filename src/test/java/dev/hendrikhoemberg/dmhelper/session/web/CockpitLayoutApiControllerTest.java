package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.web.GlobalExceptionHandler;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitBuiltInPresetCatalog;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitLayoutDocument;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleRegistry;
import dev.hendrikhoemberg.dmhelper.session.service.CockpitLayoutPresetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static dev.hendrikhoemberg.dmhelper.session.service.CockpitLayoutPresetService.PresetDto;
import static dev.hendrikhoemberg.dmhelper.session.service.CockpitLayoutPresetService.SavePresetRequest;
import static dev.hendrikhoemberg.dmhelper.session.service.CockpitLayoutPresetService.UpdatePresetRequest;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CockpitLayoutApiController.class)
@Import(GlobalExceptionHandler.class)
class CockpitLayoutApiControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean CockpitLayoutPresetService presets;
    @MockitoBean CockpitModuleRegistry modules;
    @MockitoBean CampaignRepository campaignRepository;
    private static final UUID CUSTOM_ID =
            UUID.fromString("31ae49e1-0182-44aa-bdb5-127dc75197c9");

    @Test
    void listsModulesAndPresets() throws Exception {
        var layout = new CockpitBuiltInPresetCatalog()
                .require("builtin:exploration").layout();
        when(modules.all()).thenReturn(List.of(
                CockpitModuleRegistry.standard().require("story")));
        when(presets.list()).thenReturn(List.of(
                new PresetDto("builtin:exploration", null, "Exploration",
                        true, 0, layout, List.of())));
        mvc.perform(get("/api/v1/cockpit-layout/modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("story"));
        mvc.perform(get("/api/v1/cockpit-layout/presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("builtin:exploration"));
    }

    @Test
    void createsUpdatesAndDeletesCustomPresets() throws Exception {
        var source = new CockpitBuiltInPresetCatalog()
                .require("builtin:exploration").layout();
        var layout = new CockpitLayoutDocument(1, "My Table", source.zones(),
                source.ratios(), source.compactModuleKeys());
        var dto = new PresetDto("custom:" + CUSTOM_ID, CUSTOM_ID, "My Table",
                false, 0, layout, List.of());
        when(presets.create(any())).thenReturn(dto);
        when(presets.update(eq(CUSTOM_ID), any())).thenReturn(dto);

        mvc.perform(post("/api/v1/cockpit-layout/presets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new SavePresetRequest("My Table", layout))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/cockpit-layout/presets/")));
        mvc.perform(put("/api/v1/cockpit-layout/presets/{id}", CUSTOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new UpdatePresetRequest("My Table", 0, layout))))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/cockpit-layout/presets/{id}", CUSTOM_ID))
                .andExpect(status().isNoContent());

        verify(presets).create(any());
        verify(presets).update(eq(CUSTOM_ID), any());
        verify(presets).delete(CUSTOM_ID);
    }

    @Test
    void rejectsExplicitNullPresetBodies() throws Exception {
        mvc.perform(post("/api/v1/cockpit-layout/presets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/cockpit-layout/presets/{id}", CUSTOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }
}
