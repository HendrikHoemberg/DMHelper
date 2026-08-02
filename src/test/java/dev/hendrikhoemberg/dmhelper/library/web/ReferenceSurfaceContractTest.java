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

    /** Spec 11.9: traps and hazards keep their own top-level destinations. */
    @Test
    void trapsAndHazardsKeepDistinctTopLevelDestinations() throws Exception {
        String rail = read("fragments/_rail.html");
        assertThat(rail).contains("/library/traps").contains("/library/hazards");
    }
}
