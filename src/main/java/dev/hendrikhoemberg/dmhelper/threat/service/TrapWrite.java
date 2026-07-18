package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;

import java.util.List;
import java.util.UUID;

public record TrapWrite(
        String sourceKey, String name, String description, ThreatSeverity severity,
        Integer minLevel, Integer maxLevel, String triggerDescription, String triggerAreaHint,
        Integer detectionPassiveThreshold, ThreatCheckWrite detectionCheck,
        List<TrapDisarmMethodWrite> disarmMethods, Integer attackBonus, ThreatCheckWrite save,
        String damageExpression, List<DamageType> damageTypes, String additionalEffect,
        ThreatResetMode resetMode, String resetTiming, UUID statBlockId,
        String countermeasureNotes, List<ThreatReferenceWrite> references) {
}
