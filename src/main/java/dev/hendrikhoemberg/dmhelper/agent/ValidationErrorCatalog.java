package dev.hendrikhoemberg.dmhelper.agent;

import java.util.List;

public record ValidationErrorCatalog(
        String catalogVersion,
        List<Entry> errors
) {
    public record Entry(
            String code,
            String defaultSeverity,
            String summary,
            String when,
            String suggestionTemplate,
            List<String> repairHints
    ) {}
}
