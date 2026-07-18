package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import java.util.List;

public record RollableTableEntryWrite(
        String key, Integer rangeStart, Integer rangeEnd, Integer weight,
        String resultText, String quantityExpression,
        List<RollableTableReferenceWrite> references) {
}
