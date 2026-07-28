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

    @Test
    void contentReadyEventResizesExistingBattleMap() throws IOException {
        String cockpitJs = Files.readString(
                Path.of("src/main/resources/static/js/session-cockpit.js"));
        assertThat(cockpitJs)
                .as("when map module content reloads and battleMap already exists, must resize and resume without recreating")
                .contains("cockpit:module-content-ready");
    }

    @Test
    void singleBattleMapInstanceIsNotRecreated() throws IOException {
        String cockpitJs = Files.readString(
                Path.of("src/main/resources/static/js/session-cockpit.js"));
        assertThat(cockpitJs)
                .as("constructBattleMap must guard against creating a second instance when window.battleMap exists")
                .contains("window.battleMap) return");
    }

    @Test
    void mapModuleIsPreservedInModuleSystem() throws IOException {
        String modulesJs = Files.readString(
                Path.of("src/main/resources/static/js/cockpit-modules.js"));
        assertThat(modulesJs)
                .as("cockpit module system must treat 'map' as a preserved module whose content is never replaced")
                .contains("PRESERVED_KEYS")
                .contains("'map'");
    }

    @Test
    void runtimeMapTemplateDelegatesToMapModule() throws IOException {
        String mapTemplate = Files.readString(
                Path.of("src/main/resources/templates/session/modules/_map.html"));
        assertThat(mapTemplate)
                .as("runtime map module must delegate to _map-module with MapView fields, not inline its own map UI")
                .contains("_map-module");
    }

    @Test
    void mapModuleRemovesPresentationControls() throws IOException {
        String mapModule = Files.readString(
                Path.of("src/main/resources/templates/session/_map-module.html"));
        assertThat(mapModule)
                .as("Curtain/present/preview-table controls belong in the Presentation module (Task 8), not in the map module")
                .doesNotContain("Curtain")
                .doesNotContain("Present current map")
                .doesNotContain("Preview table");
    }

    @Test
    void mapLoadsRuntimeTokensInsteadOfPlainTokens() throws IOException {
        String battleMapJs = Files.readString(
                Path.of("src/main/resources/static/js/map/battle-map.js"));
        assertThat(battleMapJs)
                .as("fetchTokens must load from runtime-tokens endpoint")
                .contains("runtime-tokens")
                .doesNotContain("/maps/${this.mapId}/tokens\"");
    }

    @Test
    void combatantMovementDispatchesToPlacementMove() throws IOException {
        String battleMapJs = Files.readString(
                Path.of("src/main/resources/static/js/map/battle-map.js"));
        assertThat(battleMapJs)
                .as("COMBATANT movement must hit placement/move endpoint")
                .contains("combatants/${token.combatantId}/placement/move");
    }

    @Test
    void markerMovementDispatchesToTokenMove() throws IOException {
        String battleMapJs = Files.readString(
                Path.of("src/main/resources/static/js/map/battle-map.js"));
        assertThat(battleMapJs)
                .as("MARKER movement must hit tokens/{id}/move endpoint")
                .contains("/tokens/${token.id}/move");
    }

    @Test
    void neverPatchesTokenHp() throws IOException {
        String battleMapJs = Files.readString(
                Path.of("src/main/resources/static/js/map/battle-map.js"));
        assertThat(battleMapJs)
                .as("HP changes must go through combatant endpoint, never tokens/{id}/hp")
                .doesNotContain("/tokens/${id}/hp")
                .doesNotContain("/tokens/${tokenId}/hp");
    }

    @Test
    void sidebarHasEncounterParticipantsAndMapMarkersSections() throws IOException {
        String mapModule = Files.readString(
                Path.of("src/main/resources/templates/session/_map-module.html"));
        assertThat(mapModule)
                .as("sidebar must label participant sections")
                .contains("Encounter participants")
                .contains("Map markers");
    }

    @Test
    void markerCreationLabel() throws IOException {
        String mapModule = Files.readString(
                Path.of("src/main/resources/templates/session/_map-module.html"));
        assertThat(mapModule)
                .as("marker creation button must say 'Add temporary marker'")
                .contains("Add temporary marker");
    }

    @Test
    void partyPlacementLabel() throws IOException {
        String mapModule = Files.readString(
                Path.of("src/main/resources/templates/session/_map-module.html"));
        assertThat(mapModule)
                .as("party placement button must say 'Place missing party members'")
                .contains("Place missing party members");
    }
}
