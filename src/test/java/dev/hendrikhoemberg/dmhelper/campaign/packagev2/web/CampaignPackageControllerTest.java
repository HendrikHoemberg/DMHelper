package dev.hendrikhoemberg.dmhelper.campaign.packagev2.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreviewStore;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportCoordinator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignImportCoordinator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationPipeline;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CampaignPackageController.class)
class CampaignPackageControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CampaignPackageValidationPipeline pipeline;
    @MockitoBean CampaignImportPreviewStore previews;
    @MockitoBean CampaignImportCoordinator importer;
    @MockitoBean CampaignExportCoordinator exporter;

    @Test
    void previewRequiresExplicitFilename() throws Exception {
        mvc.perform(post("/campaigns/package-imports/previews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_FILENAME"));
    }

    @Test
    void previewRequiresExplicitMediaType() throws Exception {
        mvc.perform(post("/campaigns/package-imports/previews")
                        .header("X-DMHelper-Filename", "campaign.dmcampaign.json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_CONTENT_TYPE"));
    }

    @Test
    void expiredPreviewIsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(importer.confirm(id, false)).thenThrow(new NoSuchElementException("internal detail"));

        mvc.perform(post("/campaigns/package-imports/{id}/confirm", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PREVIEW_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Preview not found or expired"));
    }

    @Test
    void warningGateReturnsStructuredProblem() throws Exception {
        UUID id = UUID.randomUUID();
        when(importer.confirm(id, false)).thenThrow(new IllegalArgumentException("Warnings must be accepted to proceed"));

        mvc.perform(post("/campaigns/package-imports/{id}/confirm", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_REJECTED"));
    }

    @Test
    void successfulConfirmationReturnsCampaignLocation() throws Exception {
        UUID previewId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        when(importer.confirm(previewId, true)).thenReturn(campaign);

        mvc.perform(post("/campaigns/package-imports/{id}/confirm", previewId)
                        .param("acceptWarnings", "true"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/campaigns/" + campaignId));
    }

    @Test
    void exportFailureDoesNotLeakInternalException() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(exporter.export(eq(campaignId), any(CampaignExportOptions.class)))
                .thenThrow(new IllegalStateException("secret filesystem path"));

        mvc.perform(get("/campaigns/{id}/package", campaignId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("EXPORT_FAILED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret filesystem path"))));
    }

    @Test
    void exportDefaultsToIncludeCombatLogAndDiceHistory() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(exporter.export(eq(campaignId), any(CampaignExportOptions.class)))
                .thenThrow(new IllegalStateException("expected"));

        mvc.perform(get("/campaigns/{id}/package", campaignId))
                .andExpect(status().isInternalServerError());

        verify(exporter).export(eq(campaignId),
                argThat(opts -> opts.includeCombatLog() && opts.includeDiceHistory()));
    }

    @Test
    void exportRespectsExcludeCombatLog() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(exporter.export(eq(campaignId), any(CampaignExportOptions.class)))
                .thenThrow(new IllegalStateException("expected"));

        mvc.perform(get("/campaigns/{id}/package", campaignId)
                        .param("includeCombatLog", "false"))
                .andExpect(status().isInternalServerError());

        verify(exporter).export(eq(campaignId),
                argThat(opts -> !opts.includeCombatLog() && opts.includeDiceHistory()));
    }

    @Test
    void exportRespectsExcludeDiceHistory() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(exporter.export(eq(campaignId), any(CampaignExportOptions.class)))
                .thenThrow(new IllegalStateException("expected"));

        mvc.perform(get("/campaigns/{id}/package", campaignId)
                        .param("includeDiceHistory", "false"))
                .andExpect(status().isInternalServerError());

        verify(exporter).export(eq(campaignId),
                argThat(opts -> opts.includeCombatLog() && !opts.includeDiceHistory()));
    }

    @Test
    void exportRespectsExcludeBoth() throws Exception {
        UUID campaignId = UUID.randomUUID();
        when(exporter.export(eq(campaignId), any(CampaignExportOptions.class)))
                .thenThrow(new IllegalStateException("expected"));

        mvc.perform(get("/campaigns/{id}/package", campaignId)
                        .param("includeCombatLog", "false")
                        .param("includeDiceHistory", "false"))
                .andExpect(status().isInternalServerError());

        verify(exporter).export(eq(campaignId),
                argThat(opts -> !opts.includeCombatLog() && !opts.includeDiceHistory()));
    }
}
