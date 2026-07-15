package dev.hendrikhoemberg.dmhelper.campaign.packagev2.web;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageException;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
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
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/campaigns")
public class CampaignPackageController {

    private static final Logger log = LoggerFactory.getLogger(CampaignPackageController.class);

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
    public ResponseEntity<?> createPreview(HttpServletRequest request) {
        StagedCampaignPackage staged = null;
        try {
            String contentType = request.getContentType();
            String filename = request.getHeader("X-DMHelper-Filename");
            if (filename == null || filename.isBlank()) {
                return problem(HttpStatus.BAD_REQUEST, "MISSING_FILENAME",
                        "X-DMHelper-Filename is required");
            }
            if (contentType == null || contentType.isBlank()) {
                return problem(HttpStatus.BAD_REQUEST, "MISSING_CONTENT_TYPE", "Content-Type is required");
            }

            staged = reader.read(request.getInputStream(), filename, contentType);
            var result = pipeline.validate(staged);
            var preview = previewStore.retain(result);
            staged = null; // ownership was consumed by the preview store
            return ResponseEntity.ok(preview);
        } catch (CampaignPackageException e) {
            var response = problem(HttpStatus.BAD_REQUEST, e.problem().code(), e.problem().message());
            response.getBody().setProperty("path", e.problem().path());
            if (e.problem().suggestion() != null) {
                response.getBody().setProperty("suggestion", e.problem().suggestion());
            }
            return response;
        } catch (Exception e) {
            log.warn("Campaign package preview failed", e);
            return problem(HttpStatus.BAD_REQUEST, "PACKAGE_PREVIEW_FAILED",
                    "Campaign package could not be previewed");
        } finally {
            if (staged != null) staged.close();
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
            return problem(HttpStatus.BAD_REQUEST, "CONFIRMATION_REJECTED", e.getMessage());
        } catch (NoSuchElementException e) {
            return problem(HttpStatus.NOT_FOUND, "PREVIEW_NOT_FOUND", "Preview not found or expired");
        } catch (Exception e) {
            log.error("Campaign package confirmation failed", e);
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, "IMPORT_FAILED",
                    "Campaign import failed; the preview remains available for retry");
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
            log.error("Campaign package export failed", e);
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_FAILED",
                    "Campaign package could not be exported");
        }
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle("Campaign package error");
        problem.setProperty("code", code);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
