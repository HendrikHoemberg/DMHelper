package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import java.util.List;

public record RewardTableDraft(
        String sourceText,
        List<RewardItemDraft> items) implements TableConsequenceDraft {
}
