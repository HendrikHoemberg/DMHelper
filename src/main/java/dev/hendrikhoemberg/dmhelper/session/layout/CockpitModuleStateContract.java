package dev.hendrikhoemberg.dmhelper.session.layout;

public record CockpitModuleStateContract(
        String emptyMessage,
        String loadingMessage,
        String errorMessage,
        boolean attentionSupported) {
}
