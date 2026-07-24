package dev.hendrikhoemberg.dmhelper.handout.data;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandoutAssetKindTest {

    @Test
    void defaultsToSourcePage() {
        assertThat(new Handout().getAssetKind()).isEqualTo(Handout.AssetKind.SOURCE_PAGE);
    }

    @Test
    void mapLikeKindsAreFlagged() {
        assertThat(Handout.AssetKind.TACTICAL_MAP.isMapLike()).isTrue();
        assertThat(Handout.AssetKind.REGIONAL_MAP.isMapLike()).isTrue();
        assertThat(Handout.AssetKind.PLAYER_HANDOUT.isMapLike()).isFalse();
    }

    @Test
    void rejectsNullKind() {
        assertThatThrownBy(() -> new Handout().setAssetKind(null))
                .isInstanceOf(NullPointerException.class);
    }
}
