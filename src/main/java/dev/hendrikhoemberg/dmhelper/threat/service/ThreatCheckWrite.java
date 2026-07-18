package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheckMode;

public record ThreatCheckWrite(ThreatCheckMode mode, String ability, String skill, Integer dc) {
}
