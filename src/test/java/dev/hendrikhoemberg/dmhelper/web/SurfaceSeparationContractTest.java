package dev.hendrikhoemberg.dmhelper.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-24 §D: read/run surfaces must never embed edit/admin tooling.
 * Each read/run page template and its owned fragments are scanned for
 * forbidden patterns that belong only on edit/admin surfaces.
 */
class SurfaceSeparationContractTest {

    static final Path TEMPLATES = Path.of("src/main/resources/templates");

    static final List<String> FORBIDDEN = List.of(
            "/package",           // campaign package export
            "/export",            // data export
            "hx-post=\"/campaigns/import",  // campaign import
            "hx-vals='{\"direction\"",      // reordering
            "name=\"sourceLocator\"",        // source-metadata authoring
            "data-structured-metadata"       // structured metadata authoring
    );

    /** Page template → owned fragments that must also be clean. */
    static Map<String, List<String>> readRunSurfaces() {
        return Map.ofEntries(
                Map.entry("campaigns/detail.html", List.of("campaigns/_readiness.html")),
                Map.entry("adventure/detail.html", List.of()),
                Map.entry("adventure/scene-detail.html", List.of("adventure/_scene-rail.html", "adventure/_scene-body.html")),
                Map.entry("encounter/detail.html", List.of("encounter/_prep-summary.html")),
                Map.entry("party/list.html", List.of("party/_roster.html")),
                Map.entry("session/cockpit.html", List.of("session/_cockpit-workbench.html"))
        );
    }

    @Test
    void readAndRunSurfacesEmbedNoEditOrAdminTooling() throws IOException {
        for (var entry : readRunSurfaces().entrySet()) {
            String pageHtml = Files.readString(TEMPLATES.resolve(entry.getKey()));
            assertNoForbidden(pageHtml, entry.getKey());

            for (String fragment : entry.getValue()) {
                String fragmentHtml = Files.readString(TEMPLATES.resolve(fragment));
                assertNoForbidden(fragmentHtml, fragment);
            }
        }
    }

    private static void assertNoForbidden(String html, String label) {
        for (String pattern : FORBIDDEN) {
            assertThat(html)
                    .as("%s must not contain forbidden edit/admin pattern: %s", label, pattern)
                    .doesNotContain(pattern);
        }
    }

    @Test
    void readAndRunSurfacesCarryNoDestructiveActionInTheirHeader() throws IOException {
        for (String page : readRunSurfaces().keySet()) {
            String html = Files.readString(TEMPLATES.resolve(page));
            // Extract content between page-header class="page-header-actions"
            int headerStart = html.indexOf("page-header-actions");
            if (headerStart == -1) continue; // surfaces without a page-header-actions are fine
            int sectionEnd = html.indexOf("</div>", headerStart);
            String actionsSection = html.substring(headerStart, sectionEnd);

            assertThat(actionsSection)
                    .as("%s page-header-actions must not contain btn-danger", page)
                    .doesNotContain("btn-danger");
        }
    }

    @Test
    void theCockpitContainsRuntimeModulesOnly() throws IOException {
        Path modulesDir = TEMPLATES.resolve("session/modules");
        if (!Files.isDirectory(modulesDir)) return;

        try (var files = Files.list(modulesDir)) {
            for (Path moduleFile : (Iterable<Path>) files::iterator) {
                if (!moduleFile.toString().endsWith(".html")) continue;
                String html = Files.readString(moduleFile);
                assertThat(html)
                        .as("%s must not contain sourceLocator form field", moduleFile.getFileName())
                        .doesNotContain("name=\"sourceLocator\"");
                assertThat(html)
                        .as("%s must not contain /package", moduleFile.getFileName())
                        .doesNotContain("/package");
                assertThat(html)
                        .as("%s must not contain reordering direction", moduleFile.getFileName())
                        .doesNotContain("hx-vals='{\"direction\"");
            }
        }
    }

    @Test
    void everyEditAndAdminSurfaceIsReachableFromItsReadSurface() throws IOException {
        // campaign/detail.html contains /settings
        String campaignDetail = Files.readString(TEMPLATES.resolve("campaigns/detail.html"));
        assertThat(campaignDetail)
                .as("campaigns/detail.html must link to campaign settings")
                .contains("/settings");

        // adventure/_scene-rail.html contains /structure
        String sceneRail = Files.readString(TEMPLATES.resolve("adventure/_scene-rail.html"));
        assertThat(sceneRail)
                .as("adventure/_scene-rail.html must link to scene structure")
                .contains("/structure");

        // encounter/detail.html contains /setup
        String encounterDetail = Files.readString(TEMPLATES.resolve("encounter/detail.html"));
        assertThat(encounterDetail)
                .as("encounter/detail.html must link to encounter setup")
                .contains("/setup");
    }
}
