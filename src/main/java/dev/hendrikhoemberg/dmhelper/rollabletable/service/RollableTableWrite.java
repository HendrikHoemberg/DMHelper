package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import java.util.List;

public record RollableTableWrite(
        String sourceKey, String name, String description, TableAddressMode addressMode,
        String rollExpression, TableCategory category, List<String> tags,
        List<RollableTableEntryWrite> entries) {
}
