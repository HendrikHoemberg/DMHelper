package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;

import java.util.List;

public record HazardWrite(
        String sourceKey, String name, String description, ThreatSeverity severity,
        Integer minLevel, Integer maxLevel, HazardExposureMode exposureMode,
        String exposureText, String areaHint, ThreatCheckWrite check,
        String damageExpression, List<DamageType> damageTypes,
        String escalationText, String endingConditions,
        List<ThreatReferenceWrite> references) {
}
