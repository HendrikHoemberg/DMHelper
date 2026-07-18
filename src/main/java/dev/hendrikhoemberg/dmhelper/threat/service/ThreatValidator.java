package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.dice.DiceExpressionSpec;
import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheckMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class ThreatValidator {

    private final ThreatReferenceResolver referenceResolver;

    @Autowired
    public ThreatValidator(ThreatReferenceResolver referenceResolver) {
        this.referenceResolver = referenceResolver;
    }

    /** Unit-test constructor for pure field validation without reference resolution. */
    public ThreatValidator() {
        this.referenceResolver = null;
    }

    public void validateTrap(TrapWrite write, UUID campaignIdOrNull) {
        List<ThreatValidationProblem> problems = collectTrapProblems(write, campaignIdOrNull);
        if (!problems.isEmpty()) {
            throw new ThreatValidationException(problems);
        }
    }

    public void validateHazard(HazardWrite write, UUID campaignIdOrNull) {
        List<ThreatValidationProblem> problems = collectHazardProblems(write, campaignIdOrNull);
        if (!problems.isEmpty()) {
            throw new ThreatValidationException(problems);
        }
    }

    public List<ThreatValidationProblem> collectTrapProblems(TrapWrite write, UUID campaignIdOrNull) {
        List<ThreatValidationProblem> problems = new ArrayList<>();
        if (write == null) {
            problems.add(requiredProblem("/"));
            return problems;
        }
        validateIdentity(write.sourceKey(), write.name(), write.description(), write.severity(), problems);
        validateLevels(write.minLevel(), write.maxLevel(), problems);
        validateDcOrPassive("/detectionPassiveThreshold", write.detectionPassiveThreshold(), problems);
        validateCheck("/detectionCheck", write.detectionCheck(), problems);
        validateDisarmMethods(write.disarmMethods(), problems);

        boolean hasAttack = write.attackBonus() != null;
        boolean hasSave = hasCheckContent(write.save());
        if (hasAttack && hasSave) {
            problems.add(new ThreatValidationProblem(
                    "TRAP_EFFECT_MODE_CONFLICT", "/attackBonus",
                    "Attack bonus and save are mutually exclusive"));
        }
        if (hasSave) {
            validateCheck("/save", write.save(), problems);
        }

        validateDamage(write.damageExpression(), write.damageTypes(), problems);

        if (write.resetMode() == null) {
            problems.add(requiredProblem("/resetMode"));
        } else if (write.resetMode() == ThreatResetMode.AUTOMATIC
                && (write.resetTiming() == null || write.resetTiming().isBlank())) {
            problems.add(new ThreatValidationProblem(
                    "TRAP_RESET_TIMING_REQUIRED", "/resetTiming",
                    "AUTOMATIC reset requires timing"));
        }

        if (write.statBlockId() != null) {
            validateStatBlock(write.statBlockId(), campaignIdOrNull, problems);
        }

        validateReferences(write.references(), campaignIdOrNull, problems);
        return problems;
    }

    public List<ThreatValidationProblem> collectHazardProblems(HazardWrite write, UUID campaignIdOrNull) {
        List<ThreatValidationProblem> problems = new ArrayList<>();
        if (write == null) {
            problems.add(requiredProblem("/"));
            return problems;
        }
        validateIdentity(write.sourceKey(), write.name(), write.description(), write.severity(), problems);
        validateLevels(write.minLevel(), write.maxLevel(), problems);

        if (write.exposureMode() == null) {
            problems.add(new ThreatValidationProblem(
                    "HAZARD_EXPOSURE_REQUIRED", "/exposureMode", "Exposure mode is required"));
        }

        validateCheck("/check", write.check(), problems);
        validateDamage(write.damageExpression(), write.damageTypes(), problems);
        validateReferences(write.references(), campaignIdOrNull, problems);
        return problems;
    }

    private void validateIdentity(String sourceKey, String name, String description,
                                  Object severity, List<ThreatValidationProblem> problems) {
        if (sourceKey == null || sourceKey.isBlank()) {
            problems.add(requiredProblem("/sourceKey"));
        } else if (!ThreatValidationRules.KEY_PATTERN.matcher(sourceKey).matches()) {
            problems.add(new ThreatValidationProblem(
                    "INVALID_STABLE_KEY", "/sourceKey",
                    "sourceKey must match " + ThreatValidationRules.KEY_PATTERN.pattern()));
        }
        if (name == null || name.isBlank()) {
            problems.add(requiredProblem("/name"));
        }
        if (description == null || description.isBlank()) {
            problems.add(requiredProblem("/description"));
        }
        if (severity == null) {
            problems.add(new ThreatValidationProblem(
                    "THREAT_SEVERITY_REQUIRED", "/severity", "Severity is required"));
        }
    }

    private void validateLevels(Integer minLevel, Integer maxLevel, List<ThreatValidationProblem> problems) {
        if (minLevel != null && (minLevel < ThreatValidationRules.MIN_LEVEL
                || minLevel > ThreatValidationRules.MAX_LEVEL)) {
            problems.add(new ThreatValidationProblem(
                    "THREAT_LEVEL_INVALID", "/minLevel",
                    "Level must be between " + ThreatValidationRules.MIN_LEVEL
                            + " and " + ThreatValidationRules.MAX_LEVEL));
        }
        if (maxLevel != null && (maxLevel < ThreatValidationRules.MIN_LEVEL
                || maxLevel > ThreatValidationRules.MAX_LEVEL)) {
            problems.add(new ThreatValidationProblem(
                    "THREAT_LEVEL_INVALID", "/maxLevel",
                    "Level must be between " + ThreatValidationRules.MIN_LEVEL
                            + " and " + ThreatValidationRules.MAX_LEVEL));
        }
        if (minLevel != null && maxLevel != null && minLevel > maxLevel) {
            problems.add(new ThreatValidationProblem(
                    "THREAT_LEVEL_INVALID", "/minLevel",
                    "minLevel must be less than or equal to maxLevel"));
        }
    }

    private void validateDcOrPassive(String path, Integer value, List<ThreatValidationProblem> problems) {
        if (value == null) {
            return;
        }
        if (value < ThreatValidationRules.MIN_DC || value > ThreatValidationRules.MAX_DC) {
            problems.add(new ThreatValidationProblem(
                    "THREAT_DC_OUT_OF_BOUNDS", path,
                    "Value must be between " + ThreatValidationRules.MIN_DC
                            + " and " + ThreatValidationRules.MAX_DC));
        }
    }

    private void validateCheck(String basePath, ThreatCheckWrite check,
                               List<ThreatValidationProblem> problems) {
        if (!hasCheckContent(check)) {
            return;
        }
        if (check.mode() == null) {
            problems.add(requiredProblem(basePath + "/mode"));
        }
        validateDcOrPassive(basePath + "/dc", check.dc(), problems);

        if (check.mode() == ThreatCheckMode.SAVE) {
            if (check.skill() != null && !check.skill().isBlank()) {
                problems.add(new ThreatValidationProblem(
                        "THREAT_CHECK_SAVE_HAS_SKILL", basePath + "/skill",
                        "SAVE must not include a skill"));
            }
            if (check.ability() == null || check.ability().isBlank()) {
                problems.add(new ThreatValidationProblem(
                        "THREAT_CHECK_MISSING_ABILITY_OR_SKILL", basePath + "/ability",
                        "SAVE requires an ability"));
            }
        } else if (check.mode() == ThreatCheckMode.CHECK) {
            boolean hasAbility = check.ability() != null && !check.ability().isBlank();
            boolean hasSkill = check.skill() != null && !check.skill().isBlank();
            if (!hasAbility && !hasSkill) {
                problems.add(new ThreatValidationProblem(
                        "THREAT_CHECK_MISSING_ABILITY_OR_SKILL", basePath,
                        "CHECK requires ability or skill"));
            }
        }
    }

    private void validateDisarmMethods(List<TrapDisarmMethodWrite> methods,
                                       List<ThreatValidationProblem> problems) {
        if (methods == null) {
            return;
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < methods.size(); i++) {
            TrapDisarmMethodWrite method = methods.get(i);
            String path = "/disarmMethods/" + i;
            if (method == null) {
                problems.add(requiredProblem(path));
                continue;
            }
            if (method.key() == null || method.key().isBlank()) {
                problems.add(requiredProblem(path + "/key"));
            } else if (!ThreatValidationRules.KEY_PATTERN.matcher(method.key()).matches()) {
                problems.add(new ThreatValidationProblem(
                        "INVALID_STABLE_KEY", path + "/key",
                        "Disarm method key must match " + ThreatValidationRules.KEY_PATTERN.pattern()));
            } else if (!keys.add(method.key())) {
                problems.add(new ThreatValidationProblem(
                        "DUPLICATE_DISARM_KEY", path + "/key",
                        "Disarm method key must be unique within the trap"));
            }
            if (method.label() == null || method.label().isBlank()) {
                problems.add(requiredProblem(path + "/label"));
            }
            boolean hasAbility = method.ability() != null && !method.ability().isBlank();
            boolean hasSkill = method.skill() != null && !method.skill().isBlank();
            boolean hasTool = method.tool() != null && !method.tool().isBlank();
            if (!hasAbility && !hasSkill && !hasTool) {
                problems.add(new ThreatValidationProblem(
                        "THREAT_CHECK_MISSING_ABILITY_OR_SKILL", path,
                        "Disarm method requires ability, skill, or tool"));
            }
            validateDcOrPassive(path + "/dc", method.dc(), problems);
        }
    }

    private void validateDamage(String damageExpression, List<DamageType> damageTypes,
                                List<ThreatValidationProblem> problems) {
        boolean hasExpression = damageExpression != null && !damageExpression.isBlank();
        boolean hasTypes = damageTypes != null && !damageTypes.isEmpty();
        if (hasExpression) {
            try {
                DiceExpressionSpec.parse(damageExpression);
            } catch (IllegalArgumentException e) {
                problems.add(new ThreatValidationProblem(
                        "INVALID_DAMAGE_EXPRESSION", "/damageExpression", e.getMessage()));
            }
            if (!hasTypes) {
                problems.add(new ThreatValidationProblem(
                        "DAMAGE_TYPE_REQUIRED", "/damageTypes",
                        "Damage expression requires at least one damage type"));
            }
        } else if (hasTypes) {
            problems.add(new ThreatValidationProblem(
                    "INVALID_DAMAGE_EXPRESSION", "/damageExpression",
                    "Damage types require a damage expression"));
        }
    }

    private void validateStatBlock(UUID statBlockId, UUID campaignIdOrNull,
                                   List<ThreatValidationProblem> problems) {
        if (referenceResolver == null) {
            return;
        }
        try {
            referenceResolver.requireStatBlock(campaignIdOrNull, statBlockId);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("UNRESOLVED_REFERENCE")) {
                problems.add(new ThreatValidationProblem(
                        "UNRESOLVED_REFERENCE", "/statBlockId", msg));
            } else {
                throw e;
            }
        }
    }

    private void validateReferences(List<ThreatReferenceWrite> references, UUID campaignIdOrNull,
                                    List<ThreatValidationProblem> problems) {
        if (references == null) {
            return;
        }
        for (int i = 0; i < references.size(); i++) {
            ThreatReferenceWrite ref = references.get(i);
            String basePath = "/references/" + i;
            if (ref == null) {
                problems.add(requiredProblem(basePath));
                continue;
            }
            if (ref.role() == null) {
                problems.add(requiredProblem(basePath + "/role"));
            }
            if (ref.targetType() == null) {
                problems.add(requiredProblem(basePath + "/targetType"));
            }
            if (ref.role() != null && ref.targetType() != null && !roleMatchesType(ref)) {
                problems.add(new ThreatValidationProblem(
                        "INVALID_THREAT_REFERENCE_ROLE_TYPE", basePath,
                        "Role " + ref.role() + " is incompatible with type " + ref.targetType()));
                continue;
            }
            if (ref.targetId() == null) {
                problems.add(new ThreatValidationProblem(
                        "UNRESOLVED_REFERENCE", basePath + "/targetId",
                        "targetId is required"));
                continue;
            }
            if (referenceResolver == null) {
                continue;
            }
            if (ref.role() == null || ref.targetType() == null) {
                continue;
            }
            try {
                referenceResolver.require(campaignIdOrNull, ref);
            } catch (IllegalArgumentException e) {
                String msg = e.getMessage();
                if (msg != null && msg.startsWith("UNRESOLVED_REFERENCE")) {
                    problems.add(new ThreatValidationProblem(
                            "UNRESOLVED_REFERENCE", basePath, msg));
                } else {
                    throw e;
                }
            }
        }
    }

    private boolean roleMatchesType(ThreatReferenceWrite ref) {
        if (referenceResolver != null) {
            return referenceResolver.roleMatchesType(ref);
        }
        if (ref.role() == null || ref.targetType() == null) {
            return false;
        }
        return switch (ref.role()) {
            case CONDITION -> ref.targetType()
                    == dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.CONDITION;
            case SALVAGE_ITEM -> ref.targetType()
                    == dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.EQUIPMENT_ITEM
                    || ref.targetType()
                    == dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType.MAGIC_ITEM;
        };
    }

    private static boolean hasCheckContent(ThreatCheckWrite check) {
        if (check == null) {
            return false;
        }
        return check.mode() != null
                || (check.ability() != null && !check.ability().isBlank())
                || (check.skill() != null && !check.skill().isBlank())
                || check.dc() != null;
    }

    private ThreatValidationProblem requiredProblem(String path) {
        return new ThreatValidationProblem("THREAT_FIELD_REQUIRED", path, "Field is required");
    }
}
