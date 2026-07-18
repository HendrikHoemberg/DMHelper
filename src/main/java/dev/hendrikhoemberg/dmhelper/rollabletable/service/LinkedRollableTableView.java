package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import java.util.UUID;

public record LinkedRollableTableView(
    UUID tableId,
    String sourceKey,
    String name,
    TableCategory category,
    String source,
    String sourceLabel,
    int sortOrder
) {}
