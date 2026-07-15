package dev.hendrikhoemberg.dmhelper.campaign.packagev2.web;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreview;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreviewStore;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportCoordinator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignImportCoordinator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignPackageArtifact;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationPipeline;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/campaigns")
public class CampaignPackageController {

    private final CampaignPackageReader reader;
    private final CampaignPackageValidationPipeline pipeline;
    private final CampaignImportPreviewStore previewStore;
    private final CampaignImportCoordinator importCoordinator;
    private final CampaignExportCoordinator exportCoordinator;
    private final CampaignPackageWriter writer;

    public CampaignPackageController(CampaignPackageValidationPipeline pipeline,
                                      CampaignImportPreviewStore previewStore,
                                      CampaignImportCoordinator importCoordinator,
                                      CampaignExportCoordinator exportCoordinator) {
        this.reader = new CampaignPackageReader(
                Paths.get(System.getProperty("user.home"), ".dmhelper", "import-staging"));
        this.pipeline = pipeline;
        this.previewStore = previewStore;
        this.importCoordinator = importCoordinator;
        this.exportCoordinator = exportCoordinator;
        this.writer = new CampaignPackageWriter();
    }

    @PostMapping("/package-imports/previews")
    public ResponseEntity<CampaignImportPreview> createPreview(HttpServletRequest request) {
        try {
            String contentType = request.getContentType();
            String filename = request.getHeader("X-DMHelper-Filename");
            if (filename == null) filename = "unknown";
            if (contentType == null) contentType = "application/json";

            var staged = reader.read(request.getInputStream(), filename, contentType);
            var result = pipeline.validate(staged);
            var preview = previewStore.retain(result);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/package-imports/{previewId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable UUID previewId,
                                      @RequestParam(defaultValue = "false") boolean acceptWarnings) {
        try {
            var campaign = importCoordinator.confirm(previewId, acceptWarnings);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .location(java.net.URI.create("/campaigns/" + campaign.getId()))
                    .build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
        }
    }

    @DeleteMapping("/package-imports/{previewId}")
    public ResponseEntity<Void> discard(@PathVariable UUID previewId) {
        previewStore.discard(previewId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{campaignId}/package")
    public ResponseEntity<?> exportV2(@PathVariable UUID campaignId) {
        try {
            CampaignPackageArtifact artifact = exportCoordinator.export(campaignId);
            if (artifact.zipped()) {
                return ResponseEntity.ok()
                        .contentType(artifact.mediaType())
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment()
                                        .filename(artifact.filename(), StandardCharsets.UTF_8)
                                        .build().toString())
                        .body((org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody)
                                out -> writer.write(artifact.writeRequest(), out));
            } else {
                tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();
                String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(artifact.manifest());
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .contentType(artifact.mediaType())
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment()
                                        .filename(artifact.filename(), StandardCharsets.UTF_8)
                                        .build().toString())
                        .body(bytes);
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
