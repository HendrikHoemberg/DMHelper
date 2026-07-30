package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TrackerContractTest {

    private static final Path JS = Path.of("src/main/resources/static/js/combat-tracker.js");
    private static final Path TEMPLATE = Path.of("src/main/resources/templates/encounter/_tracker.html");
    private static final Set<String> EXTERNALLY_READ = Set.of("campaignId", "trackerMode");

    @Test
    void everyStateKeyIsReadSomewhere() throws IOException {
        String js = Files.readString(JS);
        String template = Files.readString(TEMPLATE);
        List<String> dead = new ArrayList<>();
        for (String key : stateKeys(js)) {
            if (key.startsWith("_") || EXTERNALLY_READ.contains(key)) continue;
            if (template.contains(key)) continue;
            if (isReadInJs(js, key)) continue;
            dead.add(key);
        }
        assertThat(dead)
                .as("component state that is written but never read cannot affect what a DM sees")
                .isEmpty();
    }

    @Test
    void theRemovedConditionMenuStateDoesNotComeBack() throws IOException {
        assertThat(Files.readString(JS)).doesNotContain("showConditionMenu", "openConditionMenu");
    }

    private static List<String> stateKeys(String js) {
        int start = js.indexOf("function combatTracker(");
        String body = js.substring(start);
        Matcher matcher = Pattern.compile("^\\s{12}([a-zA-Z_][\\w$]*):\\s", Pattern.MULTILINE)
                .matcher(body);
        List<String> keys = new ArrayList<>();
        while (matcher.find()) keys.add(matcher.group(1));
        return keys;
    }

    @Test
    void noPerRowFieldBindsASharedModel() throws IOException {
        String row = combatantRowLoop(Files.readString(TEMPLATE));

        Matcher models = Pattern.compile("x-model=\"([^\"]+)\"").matcher(row);
        List<String> shared = new ArrayList<>();
        while (models.find()) {
            if (!models.group(1).contains("c.id")) shared.add(models.group(1));
        }
        assertThat(models.reset().find())
                .as("the row must still contain the per-row entry field this guards")
                .isTrue();
        assertThat(shared)
                .as("a per-row entry field must bind per-row state, keyed by the row's combatant")
                .isEmpty();
    }

    /**
     * The row loop contains nested templates, so the first {@code </template>} closes a child,
     * not the loop. Counting depth is the difference between guarding the whole row and
     * guarding its first fourteen lines — the latter passes no matter what the row does.
     */
    private static String combatantRowLoop(String template) {
        int start = template.indexOf("<template x-for=\"(c, idx) in combatants\"");
        assertThat(start).as("the initiative row loop must exist").isGreaterThan(-1);

        Matcher tags = Pattern.compile("<template\\b|</template>").matcher(template);
        int depth = 0;
        int cursor = start;
        while (tags.find(cursor)) {
            depth += tags.group().startsWith("</") ? -1 : 1;
            if (depth == 0) return template.substring(start, tags.start());
            cursor = tags.end();
        }
        throw new AssertionError("the initiative row loop is not closed");
    }

    private static boolean isReadInJs(String js, String key) {
        Matcher matcher = Pattern.compile("this\\." + Pattern.quote(key) + "\\b\\s*(?!=[^=])")
                .matcher(js);
        while (matcher.find()) {
            String rest = js.substring(matcher.end());
            if (!rest.startsWith("=") || rest.startsWith("==")) return true;
        }
        return false;
    }
}
