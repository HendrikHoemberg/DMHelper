package dev.hendrikhoemberg.dmhelper.campaign.packagev2.web;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CatalogSnapshot;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CampaignCatalogService catalogService;

    public CatalogController(CampaignCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<CatalogSnapshot> typedCatalog() {
        return ResponseEntity.ok(catalogService.snapshot());
    }

    @GetMapping("/snapshot")
    public ResponseEntity<String> snapshotFile() throws IOException {
        ClassPathResource resource = new ClassPathResource("catalog/srd-5.2-catalog.json");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        CatalogSnapshot snapshot = catalogService.snapshot();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .eTag("\"" + snapshot.sha256() + "\"")
                .body(content);
    }
}
