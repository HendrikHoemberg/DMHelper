package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CockpitPresentationContractTest {

    @TempDir
    Path tempDir;

    private String readBattleMapJs() throws IOException {
        Path path = Path.of(System.getProperty("user.dir"), "src/main/resources/static/js/map/battle-map.js");
        assertThat(path).exists();
        return Files.readString(path);
    }

    @Test
    void exactlyFourKonvaLayers() throws IOException {
        String content = readBattleMapJs();
        long count = content.lines()
                .filter(l -> l.contains("new Konva.Layer("))
                .count();
        assertThat(count).isEqualTo(4);
    }

    @Test
    void gridAndPinsAreKonvaGroups() throws IOException {
        String content = readBattleMapJs();
        assertThat(content).contains("new Konva.Group({ listening: false })")
                .withFailMessage("Expected a Konva.Group for grid elements");
        assertThat(content).contains("new Konva.Group({ listening: true })")
                .withFailMessage("Expected a Konva.Group for pin elements");
    }

    @Test
    void tokenGutterConstantExists() throws IOException {
        String content = readBattleMapJs();
        assertThat(content).contains("TOKEN_GUTTER_PX");
    }

    @Test
    void tokenLabelFunctionExists() throws IOException {
        String content = readBattleMapJs();
        assertThat(content).contains("function tokenLabel(");
    }

    @Test
    void kindCornerRadiusFunctionExists() throws IOException {
        String content = readBattleMapJs();
        assertThat(content).contains("function kindCornerRadius(");
    }

    @Test
    void trackerPrimaryControlsAndCopyHaveStablePresentationHooks() throws IOException {
        String tracker = read("src/main/resources/templates/encounter/_tracker.html");
        assertThat(tracker)
                .contains("data-initiative-primary")
                .contains("<details class=\"initiative-setup__tie-help\"")
                .contains("data-unset-summary")
                .contains("tracker-header__end-zone")
                .contains("End encounter")
                .contains("Negative HP shows damage beyond 0");
        assertThat(tracker).doesNotContain(">End</button>");
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir"), path));
    }
}
