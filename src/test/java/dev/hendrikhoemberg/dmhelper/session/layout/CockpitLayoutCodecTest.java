package dev.hendrikhoemberg.dmhelper.session.layout;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CockpitLayoutCodecTest {
    private final CockpitLayoutCodec codec =
            new CockpitLayoutCodec(tools.jackson.databind.json.JsonMapper.builder().build());

    @Test
    void roundTripsTheVersionedDocumentWithoutLosingOrder() {
        CockpitLayoutDocument source =
                new CockpitBuiltInPresetCatalog().require("builtin:combat").layout();
        assertThat(codec.read(codec.write(source))).isEqualTo(source);
        assertThat(codec.write(source))
                .contains("\"schemaVersion\":1", "\"moduleKeys\":[\"story\",\"party\"]");
    }

    @Test
    void rejectsUnreadableJsonWithAStableMessage() {
        assertThatThrownBy(() -> codec.read("{not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cockpit layout JSON could not be read.");
    }
}
