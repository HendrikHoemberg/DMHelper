package dev.hendrikhoemberg.dmhelper.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
 * present, unique and on the page root.
 *
 * <p>Asserted against the parsed document tree, not against source offsets: "the attribute
 * is on the main element" is a structural question, and answering it by measuring character
 * distance from `<main` breaks the moment another attribute is added.
 */
class SurfaceModeContractTest {

    static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** Governed page template -> declared surface mode. Extended as D adds pages. */
    static Map<String, String> governedSurfaces() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("campaigns/detail.html", "read");
        map.put("adventure/detail.html", "read");
        map.put("adventure/scene-detail.html", "read");
        map.put("adventure/scene-structure.html", "edit");
        map.put("encounter/detail.html", "read");
        map.put("encounter/setup.html", "edit");
        map.put("party/list.html", "read");
        map.put("session/cockpit.html", "run");
        map.put("campaigns/settings.html", "admin");
        return map;
    }

    @Test
    void everyGovernedPageDeclaresExactlyOneSurfaceMode() throws IOException {
        for (Map.Entry<String, String> entry : governedSurfaces().entrySet()) {
            Elements declarations = parse(entry.getKey()).select("[data-surface]");

            assertThat(declarations)
                    .as("%s must declare exactly one data-surface", entry.getKey())
                    .hasSize(1);
            assertThat(declarations.first().attr("data-surface"))
                    .as("%s declares the wrong surface mode", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void theDeclarationSitsOnTheRootMainElement() throws IOException {
        for (String template : governedSurfaces().keySet()) {
            Element main = parse(template).selectFirst("main");

            assertThat(main).as("%s must have a <main> element", template).isNotNull();
            assertThat(main.hasAttr("data-surface"))
                    .as("%s must declare the surface on its <main>, not deeper in the page",
                            template)
                    .isTrue();
        }
    }

    private static Document parse(String template) throws IOException {
        return Jsoup.parse(Files.readString(TEMPLATES.resolve(template)));
    }
}
