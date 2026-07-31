package dev.hendrikhoemberg.dmhelper.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-24 §D: read/run surfaces must never embed edit/admin tooling. A destructive
 * or administrative control reachable from a surface a DM uses mid-session is a safety
 * failure, not a styling one.
 *
 * <p>The forbidden-pattern scans below look for the <em>absence</em> of a bug pattern across
 * a whole file, which a substring scan answers correctly. The structural questions — "is
 * there a danger button inside the page header?", "does this page link to its editor?" —
 * are answered against the parsed tree, because slicing source at the first {@code </div>}
 * silently inspects a fragment of what it claims to.
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

    /** Read/run page → an edit or admin destination it must remain able to reach. */
    static Map<String, String> requiredDestinations() {
        return Map.of(
                "campaigns/detail.html", "/settings",
                "adventure/_scene-rail.html", "/structure",
                "encounter/detail.html", "/setup");
    }

    @Test
    void readAndRunSurfacesEmbedNoEditOrAdminTooling() throws IOException {
        for (var entry : readRunSurfaces().entrySet()) {
            assertNoForbidden(read(entry.getKey()), entry.getKey());

            for (String fragment : entry.getValue()) {
                assertNoForbidden(read(fragment), fragment);
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
        List<String> offenders = new ArrayList<>();

        for (String page : readRunSurfaces().keySet()) {
            for (Element header : parse(page).select(".page-header-actions")) {
                for (Element danger : header.select(".btn-danger")) {
                    offenders.add(page + " → " + danger.cssSelector());
                }
            }
        }

        assertThat(offenders)
                .as("a destructive control in a read/run page header is one misclick from data loss")
                .isEmpty();
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
        for (var entry : requiredDestinations().entrySet()) {
            String page = entry.getKey();
            String destination = entry.getValue();

            boolean reachable = parse(page).select("a").stream()
                    .anyMatch(link -> link.attr("th:href").contains(destination)
                            || link.attr("href").contains(destination));

            assertThat(reachable)
                    .as("%s must link to %s — an edit surface with no route into it is stranded",
                            page, destination)
                    .isTrue();
        }
    }

    private static String read(String template) throws IOException {
        return Files.readString(TEMPLATES.resolve(template));
    }

    private static Document parse(String template) throws IOException {
        return Jsoup.parse(read(template));
    }
}
