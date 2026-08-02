package dev.hendrikhoemberg.dmhelper.gamemap.web;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 13, structural only. Region *layout* — canvas share, nothing clipped, no
 * document scroll — is measured by MapEditorRenderGateTest at three viewports, which is a
 * stronger check than grepping map-editor.css for a grid declaration.
 */
class MapEditorLayoutContractTest {

    private static final Path EDITOR = Path.of("src/main/resources/templates/maps/editor.html");

    private static String markup() throws Exception {
        return Files.readString(EDITOR);
    }

    @Test
    void theEditorHasFourNamedRegions() throws Exception {
        String editor = markup();
        for (String region : List.of("mapedit__commandbar", "mapedit__rail",
                "mapedit__canvas", "mapedit__inspector")) {
            assertThat(editor).as(region).contains(region);
        }
    }

    @Test
    void everyToolLivesInTheToolRailWithALabelAndShortcut() throws Exception {
        var document = Jsoup.parse(markup(), "", Parser.xmlParser());
        var rail = document.selectFirst(".mapedit__rail");
        assertThat(rail).isNotNull();
        var tools = rail.select("[data-tool]");
        assertThat(tools.size()).as("tool count").isGreaterThanOrEqualTo(11);
        for (var tool : tools) {
            assertThat(tool.hasAttr("aria-label")).as("label on %s", tool.attr("data-tool")).isTrue();
            assertThat(tool.hasAttr("aria-pressed")).as("selected state on %s", tool.attr("data-tool")).isTrue();
            assertThat(tool.hasAttr("data-shortcut")).as("shortcut hint on %s", tool.attr("data-tool")).isTrue();
        }
    }

    @Test
    void theInspectorOwnsEverySixSection() throws Exception {
        String editor = markup();
        for (String section : List.of("tool", "palette", "selection", "layers", "map", "pins")) {
            assertThat(editor).as("inspector section %s", section)
                    .contains("data-inspector-section=\"" + section + "\"");
        }
    }
}
