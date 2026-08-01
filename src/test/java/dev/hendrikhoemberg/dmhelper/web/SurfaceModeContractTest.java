package dev.hendrikhoemberg.dmhelper.web;

import dev.hendrikhoemberg.dmhelper.support.ReleaseRehearsalFixture;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Spec 2026-07-22 section 9.1: every governed page states which of the four surface modes it
 * serves. Asserted against the rendered response, because since Task 19 the {@code <main>}
 * element comes from {@code fragments/_shell} and never appears in the page's own source.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurfaceModeContractTest {

    @Autowired MockMvc mvc;
    @Autowired ReleaseRehearsalFixture fixture;

    private ReleaseRehearsalFixture.Seeded seeded;

    @BeforeAll
    void seedOnce() throws Exception {
        seeded = fixture.seed(ReleaseRehearsalFixture.Shape.BRANCHED_TWO_MAPS);
    }

    /** Governed route -> declared surface mode. */
    private Map<String, String> governedSurfaces() {
        String campaign = "/campaigns/" + seeded.campaignId();
        String adventure = campaign + "/adventures/" + seeded.adventureId();
        String scene = adventure + "/scenes/" + seeded.hostileSceneId();
        String encounter = campaign + "/encounters/" + seeded.branchedEncounterId();

        Map<String, String> map = new LinkedHashMap<>();
        map.put(campaign, "read");
        map.put(campaign + "/settings", "admin");
        map.put(adventure, "read");
        map.put(scene, "read");
        map.put(scene + "/structure", "edit");
        map.put(encounter, "read");
        map.put(encounter + "/setup", "edit");
        map.put(campaign + "/party", "read");
        map.put(campaign + "/session", "run");
        return map;
    }

    @Test
    void everyGovernedPageDeclaresItsSurfaceOnTheMainElement() throws Exception {
        for (Map.Entry<String, String> entry : governedSurfaces().entrySet()) {
            String route = entry.getKey();
            Document page = Jsoup.parse(mvc.perform(get(route)).andReturn()
                    .getResponse().getContentAsString());

            Elements declarations = page.select("[data-surface]");
            assertThat(declarations)
                    .as("%s must declare exactly one data-surface", route).hasSize(1);
            assertThat(declarations.first().tagName())
                    .as("%s must declare the surface on <main>", route).isEqualTo("main");
            assertThat(declarations.first().attr("data-surface"))
                    .as("%s declares the wrong surface mode", route).isEqualTo(entry.getValue());
        }
    }
}
