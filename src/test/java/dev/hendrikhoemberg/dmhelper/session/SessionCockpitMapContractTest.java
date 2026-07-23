package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCockpitMapContractTest {

    @Test
    void cockpitConfigCarriesMapGeometry() throws IOException {
        String cockpit = Files.readString(
                Path.of("src/main/resources/templates/session/cockpit.html"));
        assertThat(cockpit)
                .as("battle map cell math needs the workspace map's geometry in the page config")
                .contains("cellSizePx:")
                .contains("gridWidth:")
                .contains("gridHeight:");
    }

    @Test
    void battleMapIsConstructedWithMapGeometry() throws IOException {
        String cockpitJs = Files.readString(
                Path.of("src/main/resources/static/js/session-cockpit.js"));
        assertThat(cockpitJs)
                .as("BattleMap without cellSizePx renders NaN cell math")
                .contains("cellSizePx: config.cellSizePx")
                .contains("gridWidth: config.gridWidth")
                .contains("gridHeight: config.gridHeight");
    }

    @Test
    void mapSwitchRefreshesCellSize() throws IOException {
        String battleMapJs = Files.readString(
                Path.of("src/main/resources/static/js/map/battle-map.js"));
        assertThat(battleMapJs)
                .as("switchToMap must adopt the new map's cell size, not keep the old one")
                .contains("this.cellSizePx = mapData.cellSizePx");
    }

    @Test
    void cursorReadoutUsesLiveCellSize() throws IOException {
        String battleMapJs = Files.readString(
                Path.of("src/main/resources/static/js/map/battle-map.js"));
        assertThat(battleMapJs)
                .as("a captured cell size goes stale when the map switches")
                .contains("cellPos(this.stage, this.cellSizePx)");
    }

    @Test
    void srOnlyUtilityExists() throws IOException {
        String baseCss = Files.readString(
                Path.of("src/main/resources/static/css/base.css"));
        assertThat(baseCss)
                .as("cockpit map picker label uses sr-only; without the rule it renders visibly")
                .contains(".sr-only");
    }

    @Test
    void battleMapExposesRenderingActiveControls() throws IOException {
        String battleMapJs = Files.readString(
                Path.of("src/main/resources/static/js/map/battle-map.js"));
        assertThat(battleMapJs)
                .as("hidden map modules must pause Konva without discarding world state")
                .contains("setRenderingActive")
                .contains("isRenderingActive")
                .contains("resizeToContainer")
                .contains("this.renderingActive = true");
    }

    @Test
    void sessionCockpitDefersMapInitWhenHiddenAndListensForVisibility() throws IOException {
        String cockpitJs = Files.readString(
                Path.of("src/main/resources/static/js/session-cockpit.js"));
        assertThat(cockpitJs)
                .as("map construction must wait for module visibility and use the render-active adapter")
                .contains("cockpit:module-visibility")
                .contains("isModuleVisible('map')")
                .contains("setRenderingActive")
                .contains("resizeToContainer")
                .contains("_pendingMapInit")
                .contains("_battleMapInitStarted");
    }
}
