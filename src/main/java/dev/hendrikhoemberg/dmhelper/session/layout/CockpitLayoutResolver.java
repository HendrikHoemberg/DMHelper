package dev.hendrikhoemberg.dmhelper.session.layout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@org.springframework.stereotype.Component
public final class CockpitLayoutResolver {
    private static final Logger log = LoggerFactory.getLogger(CockpitLayoutResolver.class);
    private static final String EXPLORATION_KEY = "builtin:exploration";
    private static final String UNSUPPORTED_SCHEMA_WARNING =
            "This layout uses an unsupported schema. Exploration was restored.";

    private final CockpitModuleRegistry registry;
    private final CockpitBuiltInPresetCatalog builtIns;

    public CockpitLayoutResolver(
            CockpitModuleRegistry registry, CockpitBuiltInPresetCatalog builtIns) {
        this.registry = registry;
        this.builtIns = builtIns;
    }

    public Resolution resolve(CockpitLayoutDocument layout) {
        if (layout == null || layout.schemaVersion() != CockpitLayoutDocument.CURRENT_SCHEMA_VERSION) {
            CockpitBuiltInPresetCatalog.BuiltInPreset exploration = builtIns.require(EXPLORATION_KEY);
            return new Resolution(exploration.layout(), List.of(UNSUPPORTED_SCHEMA_WARNING), EXPLORATION_KEY);
        }

        List<String> warnings = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();
        EnumMap<CockpitZone, CockpitLayoutDocument.ZoneLayout> repairedZones =
                new EnumMap<>(CockpitZone.class);

        for (CockpitZone zone : CockpitZone.values()) {
            CockpitLayoutDocument.ZoneLayout source = layout.zones() == null
                    ? null
                    : layout.zones().get(zone);
            List<String> sourceKeys = source == null || source.moduleKeys() == null
                    ? List.of()
                    : source.moduleKeys();
            List<String> kept = new ArrayList<>();
            for (String key : sourceKeys) {
                if (!registry.contains(key)
                        || usedKeys.contains(key)
                        || !registry.require(key).allowedZones().contains(zone)) {
                    warnings.add("Module '" + key + "' was omitted from " + zone);
                    continue;
                }
                usedKeys.add(key);
                kept.add(key);
            }
            String active = source == null ? null : source.activeModuleKey();
            if (kept.isEmpty()) {
                active = null;
            } else if (active == null || !kept.contains(active)) {
                active = kept.getFirst();
            }
            boolean collapsed = zone == CockpitZone.BOTTOM_UTILITY
                    && source != null
                    && source.collapsed();
            repairedZones.put(zone, new CockpitLayoutDocument.ZoneLayout(kept, active, collapsed));
        }

        Set<String> compact = new LinkedHashSet<>();
        if (layout.compactModuleKeys() != null) {
            for (String key : layout.compactModuleKeys()) {
                if (usedKeys.contains(key)
                        && registry.contains(key)
                        && registry.require(key).compactSupported()) {
                    compact.add(key);
                }
            }
        }

        CockpitLayoutDocument.SplitRatios ratios = normalizeRatios(layout.ratios());
        CockpitLayoutDocument repaired = new CockpitLayoutDocument(
                CockpitLayoutDocument.CURRENT_SCHEMA_VERSION,
                layout.name() == null || layout.name().isBlank() ? "Recovered layout" : layout.name().trim(),
                repairedZones,
                ratios,
                compact);

        try {
            new CockpitLayoutValidator(registry).validateForSave(repaired);
            return new Resolution(repaired, warnings, null);
        } catch (IllegalArgumentException validationFailed) {
            CockpitBuiltInPresetCatalog.BuiltInPreset nearest = nearestBuiltIn(repaired);
            warnings.add("Layout could not be repaired and was replaced by "
                    + nearest.name() + ".");
            return new Resolution(nearest.layout(), warnings, nearest.key());
        }
    }

    private CockpitBuiltInPresetCatalog.BuiltInPreset nearestBuiltIn(CockpitLayoutDocument candidate) {
        CockpitBuiltInPresetCatalog.BuiltInPreset best = null;
        int bestScore = Integer.MIN_VALUE;
        for (CockpitBuiltInPresetCatalog.BuiltInPreset preset : builtIns.all()) {
            int presetScore = score(candidate, preset.layout());
            if (presetScore > bestScore) {
                bestScore = presetScore;
                best = preset;
            }
        }
        return best;
    }

    private int score(CockpitLayoutDocument candidate, CockpitLayoutDocument builtIn) {
        int score = 0;
        for (CockpitZone zone : CockpitZone.values()) {
            List<String> actual = candidate.zones().getOrDefault(
                    zone, new CockpitLayoutDocument.ZoneLayout(List.of(), null, false)).moduleKeys();
            List<String> expected = builtIn.zones().get(zone).moduleKeys();
            for (String key : actual) {
                if (expected.contains(key)) {
                    score += 3;
                } else if (builtIn.zones().values().stream().anyMatch(z -> z.moduleKeys().contains(key))) {
                    score += 1;
                }
            }
            if (!actual.isEmpty() && !expected.isEmpty() && actual.getFirst().equals(expected.getFirst())) {
                score += 2;
            }
        }
        return score;
    }

    private static CockpitLayoutDocument.SplitRatios normalizeRatios(
            CockpitLayoutDocument.SplitRatios ratios) {
        double left = ratios == null ? 0.20 : ratios.left();
        double primary = ratios == null ? 0.56 : ratios.primary();
        double right = ratios == null ? 0.24 : ratios.right();
        double bottom = ratios == null ? 0.24 : ratios.bottom();

        if (!Double.isFinite(left) || left < 0) {
            left = 0;
        }
        if (!Double.isFinite(primary) || primary < 0) {
            primary = 0;
        }
        if (!Double.isFinite(right) || right < 0) {
            right = 0;
        }
        if (!Double.isFinite(bottom) || bottom < 0) {
            bottom = 0.24;
        }

        double horizontal = left + primary + right;
        if (horizontal <= 0 || !Double.isFinite(horizontal)) {
            left = 0.20;
            primary = 0.56;
            right = 0.24;
        } else {
            left /= horizontal;
            primary /= horizontal;
            right /= horizontal;
        }

        if (primary < 0.50) {
            primary = 0.50;
        } else if (primary > 0.65) {
            primary = 0.65;
        }

        double remainder = 1.0 - primary;
        double support = left + right;
        if (support <= 0 || !Double.isFinite(support)) {
            left = remainder / 2.0;
            right = remainder / 2.0;
        } else {
            left = remainder * (left / support);
            right = remainder * (right / support);
        }
        if (left <= 0) {
            left = Math.min(0.01, remainder / 2.0);
            right = remainder - left;
        }
        if (right <= 0) {
            right = Math.min(0.01, remainder / 2.0);
            left = remainder - right;
        }

        if (bottom < 0.16) {
            bottom = 0.16;
        } else if (bottom > 0.40) {
            bottom = 0.40;
        }

        return new CockpitLayoutDocument.SplitRatios(left, primary, right, bottom);
    }

    public Resolution resolve(String presetId, CockpitZone... zones) {
        try {
            var preset = builtIns.require(presetId);
            return resolve(preset.layout());
        } catch (IllegalArgumentException e) {
            log.debug("Built-in preset '{}' not found, falling back to '{}'", presetId, EXPLORATION_KEY);
            var exploration = builtIns.require(EXPLORATION_KEY);
            return new Resolution(exploration.layout(), List.of(), EXPLORATION_KEY);
        }
    }

    public record Resolution(
            CockpitLayoutDocument document,
            List<String> warnings,
            String fallbackKey) {
        public Resolution {
            warnings = List.copyOf(warnings);
        }
    }
}
