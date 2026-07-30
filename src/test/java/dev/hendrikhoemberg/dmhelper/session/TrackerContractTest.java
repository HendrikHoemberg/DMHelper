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
        String template = Files.readString(TEMPLATE);
        int start = template.indexOf("x-for=\"(c, idx) in combatants\"");
        int end = template.indexOf("</template>", start);
        String loopBody = template.substring(start, end);
        Pattern pattern = Pattern.compile("x-model=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(loopBody);
        List<String> models = new ArrayList<>();
        while (matcher.find()) {
            models.add(matcher.group(1));
        }
        assertThat(models).allMatch(m -> m.contains("c.id"));
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
