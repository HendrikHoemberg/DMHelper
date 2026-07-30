package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

class ConditionDefaultsTest {
    private static final Map<String, Integer> EXPECTED = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("blinded", 0), Map.entry("charmed", 0), Map.entry("deafened", 0),
            Map.entry("exhaustion", 0), Map.entry("frightened", 0), Map.entry("grappled", 0),
            Map.entry("incapacitated", 0), Map.entry("invisible", 0), Map.entry("paralyzed", 0),
            Map.entry("petrified", 0), Map.entry("poisoned", 0), Map.entry("prone", 0),
            Map.entry("restrained", 0), Map.entry("stunned", 0), Map.entry("unconscious", 0)));

    @Test
    void everyCatalogueConditionDefaultsToIndefinite() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/combat-tracker.js"));
        Matcher matcher = Pattern.compile("^\\s*([a-z]+):\\s*(\\d+),\\s*$", Pattern.MULTILINE)
                .matcher(defaultsBlock(js));
        Map<String, Integer> actual = new LinkedHashMap<>();
        while (matcher.find()) actual.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(EXPECTED);
    }

    @Test
    void theCatalogueLoaderDoesNotOverrideTheDefaults() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/combat-tracker.js"));
        assertThat(js)
                .as("a server-supplied condition must not be coerced back to one round")
                .doesNotContain("defaultDuration: 1")
                .doesNotContain("durationRounds: duration || 1");
    }

    private static String defaultsBlock(String js) {
        int start = js.indexOf("const CONDITION_DEFAULT_DURATIONS");
        assertThat(start).as("CONDITION_DEFAULT_DURATIONS must exist").isGreaterThan(-1);
        return js.substring(start, js.indexOf("};", start));
    }
}
