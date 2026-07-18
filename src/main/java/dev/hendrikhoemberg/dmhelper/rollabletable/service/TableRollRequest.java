package dev.hendrikhoemberg.dmhelper.rollabletable.service;

public record TableRollRequest(
        Integer manualValue, int rollCount, TableDuplicatePolicy duplicatePolicy) {
}
