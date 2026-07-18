package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheckMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReferenceRole;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThreatValidatorTest {

    private final ThreatValidator validator = new ThreatValidator();

    @Test
    void rejectsBlankIdentityAndMissingSeverity() {
        TrapWrite write = trapBase(" ", " ", " ", null, null, null);

        assertProblems(write,
                "THREAT_FIELD_REQUIRED:/sourceKey",
                "THREAT_FIELD_REQUIRED:/name",
                "THREAT_FIELD_REQUIRED:/description",
                "THREAT_SEVERITY_REQUIRED:/severity");
    }

    @Test
    void rejectsInvalidStableKey() {
        TrapWrite write = trapBase("Bad Key", "Needle", "A trap.", ThreatSeverity.SETBACK, null, null);
        assertProblem(write, "INVALID_STABLE_KEY", "/sourceKey");
    }

    @Test
    void rejectsPartialReversedAndOutOfBoundLevels() {
        TrapWrite reversed = trapBase("a", "A", "desc", ThreatSeverity.SETBACK, 10, 3);
        assertProblem(reversed, "THREAT_LEVEL_INVALID", "/minLevel");

        TrapWrite outOfBound = trapBase("b", "B", "desc", ThreatSeverity.SETBACK, 0, 21);
        assertThatThrownBy(() -> validator.validateTrap(outOfBound, null))
                .isInstanceOfSatisfying(ThreatValidationException.class, ex ->
                        assertThat(ex.problems())
                                .extracting(ThreatValidationProblem::code)
                                .contains("THREAT_LEVEL_INVALID"));
    }

    @Test
    void rejectsDcAndPassiveOutOfBounds() {
        TrapWrite write = new TrapWrite(
                "needle", "Needle", "desc", ThreatSeverity.DANGEROUS,
                1, 5, null, null, 99,
                new ThreatCheckWrite(ThreatCheckMode.CHECK, "WIS", "Perception", 50),
                List.of(), null, null, null, List.of(), null,
                ThreatResetMode.NONE, null, null, null, List.of());

        assertThatThrownBy(() -> validator.validateTrap(write, null))
                .isInstanceOfSatisfying(ThreatValidationException.class, ex ->
                        assertThat(ex.problems())
                                .extracting(ThreatValidationProblem::code)
                                .contains("THREAT_DC_OUT_OF_BOUNDS"));
    }

    @Test
    void rejectsSaveWithSkillAndCheckWithoutAbilityOrSkill() {
        TrapWrite saveWithSkill = new TrapWrite(
                "save-skill", "Save", "desc", ThreatSeverity.SETBACK,
                null, null, null, null, null, null, List.of(), null,
                new ThreatCheckWrite(ThreatCheckMode.SAVE, "DEX", "Acrobatics", 12),
                null, List.of(), null, ThreatResetMode.NONE, null, null, null, List.of());
        assertProblem(saveWithSkill, "THREAT_CHECK_SAVE_HAS_SKILL", "/save/skill");

        TrapWrite checkBare = new TrapWrite(
                "check-bare", "Check", "desc", ThreatSeverity.SETBACK,
                null, null, null, null, null,
                new ThreatCheckWrite(ThreatCheckMode.CHECK, null, null, 12),
                List.of(), null, null, null, List.of(), null,
                ThreatResetMode.NONE, null, null, null, List.of());
        assertProblem(checkBare, "THREAT_CHECK_MISSING_ABILITY_OR_SKILL", "/detectionCheck");
    }

    @Test
    void rejectsAttackAndSaveConflict() {
        TrapWrite write = new TrapWrite(
                "conflict", "Conflict", "desc", ThreatSeverity.DANGEROUS,
                null, null, null, null, null, null, List.of(), 5,
                new ThreatCheckWrite(ThreatCheckMode.SAVE, "DEX", null, 13),
                null, List.of(), null, ThreatResetMode.NONE, null, null, null, List.of());

        assertThatThrownBy(() -> validator.validateTrap(write, null))
                .isInstanceOfSatisfying(ThreatValidationException.class, ex ->
                        assertThat(ex.problems()).contains(
                                new ThreatValidationProblem(
                                        "TRAP_EFFECT_MODE_CONFLICT", "/attackBonus",
                                        "Attack bonus and save are mutually exclusive")));
    }

    @Test
    void rejectsBadDamageExpressionAndDamageWithoutType() {
        TrapWrite badExpr = new TrapWrite(
                "bad-dmg", "Bad", "desc", ThreatSeverity.SETBACK,
                null, null, null, null, null, null, List.of(), null, null,
                "not-dice", List.of(DamageType.FIRE), null,
                ThreatResetMode.NONE, null, null, null, List.of());
        assertProblem(badExpr, "INVALID_DAMAGE_EXPRESSION", "/damageExpression");

        TrapWrite noType = new TrapWrite(
                "no-type", "NoType", "desc", ThreatSeverity.SETBACK,
                null, null, null, null, null, null, List.of(), null, null,
                "1d6", List.of(), null,
                ThreatResetMode.NONE, null, null, null, List.of());
        assertProblem(noType, "DAMAGE_TYPE_REQUIRED", "/damageTypes");
    }

    @Test
    void rejectsDuplicateDisarmKeysAndMethodWithoutAbilitySkillOrTool() {
        TrapWrite write = new TrapWrite(
                "disarm", "Disarm", "desc", ThreatSeverity.SETBACK,
                null, null, null, null, null, null,
                List.of(
                        new TrapDisarmMethodWrite("same", "A", "DEX", null, null, 10, null, 0),
                        new TrapDisarmMethodWrite("same", "B", "INT", null, null, 10, null, 1),
                        new TrapDisarmMethodWrite("empty", "C", null, null, null, 10, null, 2)),
                null, null, null, List.of(), null,
                ThreatResetMode.NONE, null, null, null, List.of());

        assertThatThrownBy(() -> validator.validateTrap(write, null))
                .isInstanceOfSatisfying(ThreatValidationException.class, ex -> {
                    assertThat(ex.problems())
                            .anyMatch(p -> p.code().equals("DUPLICATE_DISARM_KEY"));
                    assertThat(ex.problems())
                            .anyMatch(p -> p.code().equals("THREAT_CHECK_MISSING_ABILITY_OR_SKILL")
                                    && p.path().equals("/disarmMethods/2"));
                });
    }

    @Test
    void rejectsAutomaticResetWithoutTiming() {
        TrapWrite write = new TrapWrite(
                "auto", "Auto", "desc", ThreatSeverity.SETBACK,
                null, null, null, null, null, null, List.of(), null, null,
                null, List.of(), null,
                ThreatResetMode.AUTOMATIC, " ", null, null, List.of());
        assertProblem(write, "TRAP_RESET_TIMING_REQUIRED", "/resetTiming");
    }

    @Test
    void rejectsInvalidRoleTypeAndMissingTarget() {
        TrapWrite badRole = new TrapWrite(
                "bad-role", "Bad", "desc", ThreatSeverity.SETBACK,
                null, null, null, null, null, null, List.of(), null, null,
                null, List.of(), null, ThreatResetMode.NONE, null, null, null,
                List.of(new ThreatReferenceWrite(
                        ThreatReferenceRole.CONDITION, CampaignContentType.STATBLOCK,
                        UUID.randomUUID(), "Wrong")));
        assertProblem(badRole, "INVALID_THREAT_REFERENCE_ROLE_TYPE", "/references/0");

        TrapWrite missingTarget = new TrapWrite(
                "miss-target", "Miss", "desc", ThreatSeverity.SETBACK,
                null, null, null, null, null, null, List.of(), null, null,
                null, List.of(), null, ThreatResetMode.NONE, null, null, null,
                List.of(new ThreatReferenceWrite(
                        ThreatReferenceRole.CONDITION, CampaignContentType.CONDITION,
                        null, "Poisoned")));
        assertProblem(missingTarget, "UNRESOLVED_REFERENCE", "/references/0/targetId");
    }

    @Test
    void rejectsMissingHazardExposure() {
        HazardWrite write = new HazardWrite(
                "slime", "Slime", "desc", ThreatSeverity.SETBACK,
                null, null, null, null, null, null, null, List.of(), null, null, List.of());
        assertThatThrownBy(() -> validator.validateHazard(write, null))
                .isInstanceOfSatisfying(ThreatValidationException.class, ex ->
                        assertThat(ex.problems()).anyMatch(p ->
                                p.code().equals("HAZARD_EXPOSURE_REQUIRED")));
    }

    @Test
    void acceptsValidTrapAndHazard() {
        TrapWrite trap = new TrapWrite(
                "poisoned-needle", "Poisoned Needle", "A needle in a lock.",
                ThreatSeverity.DANGEROUS, 3, 7, "Opening the lock", "lock plate", 15,
                new ThreatCheckWrite(ThreatCheckMode.CHECK, "WIS", "Perception", 15),
                List.of(new TrapDisarmMethodWrite(
                        "jam-gears", "Jam gears", "DEX", "Sleight of Hand", null, 14, "Fires", 0)),
                null,
                new ThreatCheckWrite(ThreatCheckMode.SAVE, "CON", null, 13),
                "1d10", List.of(DamageType.PIERCING, DamageType.POISON),
                "Poisoned for 1 hour", ThreatResetMode.MANUAL, null, null,
                "Gloves help", List.of());

        HazardWrite hazard = new HazardWrite(
                "green-slime", "Green Slime", "Corrosive slime.",
                ThreatSeverity.SETBACK, 1, 4, HazardExposureMode.ON_ENTER,
                "When entered", "10-ft square",
                new ThreatCheckWrite(ThreatCheckMode.SAVE, "DEX", null, 12),
                "1d6", List.of(DamageType.ACID), "Spreads", "Fire ends it", List.of());

        assertThatCode(() -> validator.validateTrap(trap, null)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateHazard(hazard, null)).doesNotThrowAnyException();
    }

    private void assertProblem(TrapWrite write, String code, String path) {
        assertThatThrownBy(() -> validator.validateTrap(write, null))
                .isInstanceOfSatisfying(ThreatValidationException.class, ex ->
                        assertThat(ex.problems())
                                .anyMatch(p -> p.code().equals(code) && p.path().equals(path)));
    }

    private void assertProblems(TrapWrite write, String... codePaths) {
        assertThatThrownBy(() -> validator.validateTrap(write, null))
                .isInstanceOfSatisfying(ThreatValidationException.class, ex -> {
                    List<String> actual = ex.problems().stream()
                            .map(p -> p.code() + ":" + p.path())
                            .toList();
                    assertThat(actual).contains(codePaths);
                });
    }

    private TrapWrite trapBase(String key, String name, String description,
                               ThreatSeverity severity, Integer min, Integer max) {
        return new TrapWrite(
                key, name, description, severity, min, max,
                null, null, null, null, List.of(), null, null, null, List.of(), null,
                ThreatResetMode.NONE, null, null, null, List.of());
    }
}
