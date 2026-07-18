package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.dice.DiceResult;

import java.util.List;

public record TableRollOutcome(
        String tableKey,
        String tableName,
        DiceResult rawRoll,
        String entryKey,
        String resultText,
        DiceResult quantityRoll,
        List<TableResolvedReference> references,
        List<TableRollOutcome> nestedRolls) {
}
