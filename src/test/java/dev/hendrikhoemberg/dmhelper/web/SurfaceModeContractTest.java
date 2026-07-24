package dev.hendrikhoemberg.dmhelper.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 9.1: every governed page states which of the four surface modes
 * it serves. The declaration is what the separation guard later keys off, so it has to be
 * present, unique and spelled correctly.
 */
class SurfaceModeContractTest {

    static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** Governed page template -> declared surface mode. Extended as D adds pages. */
    static Map<String, String> governedSurfaces() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("campaigns/detail.html", "read");
        map.put("adventure/detail.html", "read");
        map.put("adventure/scene-detail.html", "read");
        map.put("encounter/detail.html", "read");
        map.put("party/list.html", "read");
        map.put("session/cockpit.html", "run");
        map.put("campaigns/settings.html", "admin");
        return map;
    }

    @Test
    void everyGovernedPageDeclaresExactlyOneSurfaceMode() throws IOException {
        for (Map.Entry<String, String> entry : governedSurfaces().entrySet()) {
            String html = Files.readString(TEMPLATES.resolve(entry.getKey()));
            assertThat(occurrences(html, "data-surface=\""))
                    .as("%s must declare exactly one data-surface", entry.getKey())
                    .isEqualTo(1);
            assertThat(html)
                    .as("%s must declare data-surface=\"%s\"", entry.getKey(), entry.getValue())
                    .contains("data-surface=\"" + entry.getValue() + "\"");
        }
    }

    @Test
    void theDeclarationSitsOnTheRootMainElement() throws IOException {
        for (String template : governedSurfaces().keySet()) {
            String html = Files.readString(TEMPLATES.resolve(template));
            int mainAt = html.indexOf("<main");
            int surfaceAt = html.indexOf("data-surface=\"");
            assertThat(mainAt).as("%s must have a <main> element", template).isGreaterThan(-1);
            assertThat(surfaceAt)
                    .as("%s must declare the surface on its <main>, not deeper in the page", template)
                    .isBetween(mainAt, mainAt + 200);
        }
    }

    static int occurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
