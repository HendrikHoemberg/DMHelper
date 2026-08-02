package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenColorsTest {

    @Test
    void aPlainHexTokenResolvesToItself() {
        assertThat(TokenColors.resolve("--surface-canvas")).isEqualTo("#101113");
    }

    @Test
    void anAliasResolvesThroughToTheRoleItPointsAt() {
        assertThat(TokenColors.resolve("--selection-accent"))
                .isEqualTo(TokenColors.resolve("--action-primary"));
    }

    /** 14% of #d96d64 over #1d1f24, the mix --state-danger-surface declares. */
    @Test
    void aColorMixResolvesToTheChannelBlendCssWouldPaint() {
        assertThat(TokenColors.resolve("--state-danger-surface")).isEqualTo("#372a2d");
    }

    @Test
    void anUndeclaredTokenIsRejectedLoudly() {
        assertThatThrownBy(() -> TokenColors.resolve("--surface-imaginary"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not declared");
    }

    /**
     * Mixing with transparent yields an alpha, and contrast against an unknown backdrop is
     * undefined — the resolver must say so rather than invent an opaque answer.
     */
    @Test
    void aTranslucentTokenIsRejectedRatherThanGuessed() {
        assertThatThrownBy(() -> TokenColors.resolve("--selection-surface"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("opaque");
    }
}
