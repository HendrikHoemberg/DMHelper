package dev.hendrikhoemberg.dmhelper.adventure.data;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SceneMapRequirementTest {

    @Test
    void mapRequirementIsNullByDefaultMeaningInfer() {
        assertThat(new Scene().getMapRequirement()).isNull();
    }

    @Test
    void mapRequirementIsSettable() {
        Scene scene = new Scene();
        scene.setMapRequirement(SceneMapRequirement.REQUIRED);
        assertThat(scene.getMapRequirement()).isEqualTo(SceneMapRequirement.REQUIRED);
    }
}
