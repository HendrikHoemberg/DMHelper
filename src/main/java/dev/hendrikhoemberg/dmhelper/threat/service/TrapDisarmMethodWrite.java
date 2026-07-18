package dev.hendrikhoemberg.dmhelper.threat.service;

public record TrapDisarmMethodWrite(
        String key, String label, String ability, String skill, String tool,
        Integer dc, String failureConsequence, int sortOrder) {
}
