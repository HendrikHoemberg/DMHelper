package dev.hendrikhoemberg.dmhelper.library.web;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ReferenceSurfaceContractTest {

    static final List<String> CATEGORIES = List.of("Monsters", "Spells", "Conditions", "Rules",
            "Equipment", "Magic Items", "Classes", "Species", "Backgrounds", "Feats");

    private static String read(String template) throws Exception {
        return Files.readString(Path.of("src/main/resources/templates").resolve(template));
    }

    /** A category missing from the navigator is unreachable and invisible until searched for. */
    @Test
    void theCategoryNavigatorIsStableAndComplete() throws Exception {
        String list = read("library/list.html");
        for (String category : CATEGORIES) {
            assertThat(list).as("navigator entry %s", category)
                    .contains("data-library-category=\"" + category + "\"");
        }
    }

    /** Spec 11.9 names the ambiguous "New Homebrew" copy as the defect being removed. */
    @Test
    void theCreationActionNamesTheContentType() throws Exception {
        assertThat(read("library/list.html")).doesNotContain("New Homebrew");
    }

    /**
     * Spec 11.9 keeps search and category filters visible while browsing, and spec 17 requires
     * them to be operable. Library is the one Part 3 index that does not use the shared
     * toolbar; its filters are hand-rolled, and every one of the 28 of them was a bare
     * {@code <label>} sitting next to its control rather than associated with it, so none of
     * them had an accessible name.
     */
    @Test
    void everyLibraryFilterControlHasAnAccessibleName() throws Exception {
        var document = org.jsoup.Jsoup.parse(read("library/list.html"));
        var labelled = document.select("label[for]").stream()
                .map(label -> label.attr("for"))
                .collect(java.util.stream.Collectors.toSet());

        var controls = document.select(".search-bar input, .search-bar select");
        assertThat(controls).as("no library filter control was scanned").isNotEmpty();
        assertThat(controls).allSatisfy(control -> assertThat(
                        labelled.contains(control.id())
                                || control.hasAttr("aria-label")
                                || control.parents().stream().anyMatch(p -> p.tagName().equals("label")))
                .as("control %s has no associated label", control.attr("name") + "/" + control.id())
                .isTrue());
    }

    /** Spec 11.9: traps and hazards keep their own top-level destinations. */
    @Test
    void trapsAndHazardsKeepDistinctTopLevelDestinations() throws Exception {
        String rail = read("fragments/_rail.html");
        assertThat(rail).contains("/library/traps").contains("/library/hazards");
    }
}
