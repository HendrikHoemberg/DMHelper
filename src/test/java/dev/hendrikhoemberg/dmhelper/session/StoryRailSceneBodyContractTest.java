package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class StoryRailSceneBodyContractTest {

    @Test
    void sceneBodyIsNotHeightClamped() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/static/css/cockpit.css"));
        Matcher rule = Pattern.compile("\\.scene-body\\s*\\{([^}]*)}").matcher(css);

        assertThat(rule.find()).as(".scene-body rule must exist in cockpit.css").isTrue();
        String body = rule.group(1);

        assertThat(body)
                .as(".scene-body must not clamp its height — the tail becomes unreachable")
                .doesNotContain("max-height")
                .doesNotContain("overflow: hidden")
                .doesNotContain("overflow:hidden")
                .doesNotContain("-webkit-line-clamp");
    }
}
