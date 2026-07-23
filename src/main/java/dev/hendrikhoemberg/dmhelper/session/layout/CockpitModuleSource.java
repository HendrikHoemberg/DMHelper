package dev.hendrikhoemberg.dmhelper.session.layout;

public record CockpitModuleSource(Kind kind, String value) {
    public enum Kind { THYMELEAF_FRAGMENT, ENDPOINT }

    public CockpitModuleSource {
        if (kind == null || value == null || value.isBlank()) {
            throw new IllegalArgumentException("Module source requires kind and value.");
        }
    }
}
