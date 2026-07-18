package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.dice.DiceExpressionSpec;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class RollableTableValidator {

    private static final Set<CampaignContentType> ALLOWED_REFERENCE_TYPES = Set.of(
            CampaignContentType.STATBLOCK, CampaignContentType.EQUIPMENT_ITEM,
            CampaignContentType.MAGIC_ITEM, CampaignContentType.NOTE,
            CampaignContentType.ROLLABLE_TABLE, CampaignContentType.ENCOUNTER,
            CampaignContentType.HANDOUT
    );

    private static final int MAX_REFERENCE_DEPTH = 5;

    public void validate(RollableTableWrite write, UUID currentTableId) {
        List<TableValidationProblem> problems = collectProblems(write, currentTableId);
        if (!problems.isEmpty()) {
            throw new RollableTableValidationException(problems);
        }
    }

    public List<TableValidationProblem> collectProblems(RollableTableWrite write, UUID currentTableId) {
        List<TableValidationProblem> problems = new ArrayList<>();
        validateTableFields(write, problems);
        validateEntries(write, problems);
        validateReferences(write, problems);
        validateGraph(write, currentTableId, problems);
        return problems;
    }

    private void validateTableFields(RollableTableWrite write, List<TableValidationProblem> problems) {
        if (write.rollExpression() != null && !write.rollExpression().isBlank()) {
            try {
                DiceExpressionSpec.parse(write.rollExpression());
            } catch (IllegalArgumentException e) {
                problems.add(new TableValidationProblem(
                        "INVALID_TABLE_EXPRESSION", "/rollExpression", e.getMessage()));
            }
        }
    }

    private void validateEntries(RollableTableWrite write, List<TableValidationProblem> problems) {
        if (write.entries() == null) return;

        if (write.addressMode() == TableAddressMode.RANGE) {
            validateRangeEntries(write, problems);
        } else if (write.addressMode() == TableAddressMode.WEIGHTED) {
            validateWeightedEntries(write, problems);
        }
    }

    private void validateRangeEntries(RollableTableWrite write, List<TableValidationProblem> problems) {
        var sortedWithIndex = new ArrayList<EntryWithIndex>();
        for (int i = 0; i < write.entries().size(); i++) {
            sortedWithIndex.add(new EntryWithIndex(write.entries().get(i), i));
        }
        sortedWithIndex.sort(Comparator.comparingInt(
                ewi -> ewi.entry.rangeStart() != null ? ewi.entry.rangeStart() : Integer.MAX_VALUE));

        // Validate each entry has valid range
        for (var ewi : sortedWithIndex) {
            var entry = ewi.entry;
            if (entry.rangeStart() == null || entry.rangeEnd() == null) {
                problems.add(new TableValidationProblem(
                        "TABLE_RANGE_BOUNDS", "/entries/" + ewi.index + "/rangeStart",
                        "Range entry must have rangeStart and rangeEnd"));
                continue;
            }
            if (entry.rangeStart() > entry.rangeEnd()) {
                problems.add(new TableValidationProblem(
                        "TABLE_RANGE_BOUNDS", "/entries/" + ewi.index + "/rangeStart",
                        "rangeStart must be <= rangeEnd"));
                continue;
            }
            if (entry.weight() != null) {
                problems.add(new TableValidationProblem(
                        "TABLE_WEIGHT_INVALID", "/entries/" + ewi.index + "/weight",
                        "Range entries cannot have weight"));
            }
        }

        // Check for gap, overlap, bounds
        DiceExpressionSpec rollSpec = null;
        if (write.rollExpression() != null && !write.rollExpression().isBlank()) {
            try {
                rollSpec = DiceExpressionSpec.parse(write.rollExpression());
            } catch (IllegalArgumentException ignored) {
            }
        }

        int expectedMin = rollSpec != null ? rollSpec.min() : 1;
        int expectedMax = rollSpec != null ? rollSpec.max() : 0;
        int previousEnd = 0;

        for (var ewi : sortedWithIndex) {
            var entry = ewi.entry;
            if (entry.rangeStart() == null || entry.rangeEnd() == null) continue;

            if (entry.rangeStart() != previousEnd + 1) {
                if (previousEnd == 0) {
                    if (entry.rangeStart() != expectedMin) {
                        problems.add(new TableValidationProblem(
                                "TABLE_RANGE_BOUNDS", "/entries/" + ewi.index + "/rangeStart",
                                "First entry rangeStart should be " + expectedMin + " but was " + entry.rangeStart()));
                    }
                } else if (entry.rangeStart() < previousEnd + 1) {
                    problems.add(new TableValidationProblem(
                            "TABLE_RANGE_OVERLAP", "/entries/" + ewi.index + "/rangeStart",
                            "Range overlaps with previous entry ending at " + previousEnd));
                } else {
                    problems.add(new TableValidationProblem(
                            "TABLE_RANGE_GAP", "/entries/" + ewi.index + "/rangeStart",
                            "Gap between range end " + previousEnd + " and start " + entry.rangeStart()));
                }
            }
            previousEnd = entry.rangeEnd();
        }

        // Check last entry covers the max
        if (rollSpec != null && expectedMax > 0 && previousEnd < expectedMax && !sortedWithIndex.isEmpty()) {
            problems.add(new TableValidationProblem(
                    "TABLE_RANGE_BOUNDS", "/entries/" + sortedWithIndex.getLast().index + "/rangeEnd",
                    "Last entry ends at " + previousEnd + " but roll max is " + expectedMax));
        }
    }

    private void validateWeightedEntries(RollableTableWrite write, List<TableValidationProblem> problems) {
        for (int i = 0; i < write.entries().size(); i++) {
            var entry = write.entries().get(i);
            if (entry.rangeStart() != null || entry.rangeEnd() != null) {
                problems.add(new TableValidationProblem(
                        "TABLE_WEIGHT_INVALID", "/entries/" + i + "/weight",
                        "Weighted entries must not have range fields"));
            }
            if (entry.weight() == null || entry.weight() <= 0) {
                problems.add(new TableValidationProblem(
                        "TABLE_WEIGHT_INVALID", "/entries/" + i + "/weight",
                        "Weight must be > 0"));
            }
            if (entry.quantityExpression() != null && !entry.quantityExpression().isBlank()) {
                try {
                    DiceExpressionSpec.parse(entry.quantityExpression());
                } catch (IllegalArgumentException e) {
                    problems.add(new TableValidationProblem(
                            "INVALID_QUANTITY_EXPRESSION", "/entries/" + i + "/quantityExpression",
                            e.getMessage()));
                }
            }
            boolean hasResultText = entry.resultText() != null && !entry.resultText().isBlank();
            boolean hasReferences = entry.references() != null && !entry.references().isEmpty();
            if (!hasResultText && !hasReferences) {
                problems.add(new TableValidationProblem(
                        "TABLE_WEIGHT_INVALID", "/entries/" + i + "/weight",
                        "Entry must have resultText or at least one reference"));
            }
        }

        // Validate total weight does not overflow
        if (problems.isEmpty()) {
            try {
                int total = 0;
                for (var entry : write.entries()) {
                    if (entry.weight() != null) {
                        total = Math.addExact(total, entry.weight());
                    }
                }
            } catch (ArithmeticException e) {
                problems.add(new TableValidationProblem(
                        "TABLE_WEIGHT_INVALID", "/entries/0/weight",
                        "Total weight overflow"));
            }
        }
    }

    private void validateReferences(RollableTableWrite write, List<TableValidationProblem> problems) {
        if (write.entries() == null) return;
        for (int ei = 0; ei < write.entries().size(); ei++) {
            var entry = write.entries().get(ei);
            if (entry.references() == null) continue;
            for (int ri = 0; ri < entry.references().size(); ri++) {
                var ref = entry.references().get(ri);
                String basePath = "/entries/" + ei + "/references/" + ri;

                if (!ALLOWED_REFERENCE_TYPES.contains(ref.targetType())) {
                    problems.add(new TableValidationProblem(
                            "INVALID_TABLE_REFERENCE_TYPE", basePath,
                            "Reference type " + ref.targetType() + " is not allowed"));
                }

                if (ref.scope() == null) {
                    problems.add(new TableValidationProblem(
                            "INVALID_TABLE_REFERENCE_TYPE", basePath,
                            "Reference scope is required"));
                }
            }
        }
    }

    private void validateGraph(RollableTableWrite write, UUID currentTableId, List<TableValidationProblem> problems) {
        List<TableValidationProblem> graphProblems = new ArrayList<>();
        dfsValidate(write, currentTableId, new HashSet<>(), new ArrayList<>(), 0, graphProblems);
        problems.addAll(graphProblems);
    }

    private void dfsValidate(RollableTableWrite write, UUID currentTableId,
                             Set<UUID> visiting, List<UUID> path, int depth,
                             List<TableValidationProblem> problems) {
        if (write.entries() == null) return;
        for (int ei = 0; ei < write.entries().size(); ei++) {
            var entry = write.entries().get(ei);
            if (entry.references() == null) continue;
            for (int ri = 0; ri < entry.references().size(); ri++) {
                var ref = entry.references().get(ri);
                if (ref.scope() != null && ref.scope() == dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope.ENTITY
                        && ref.targetType() == CampaignContentType.ROLLABLE_TABLE
                        && ref.targetId() != null) {
                    String basePath = "/entries/" + ei + "/references/" + ri;

                    if (depth >= MAX_REFERENCE_DEPTH) {
                        problems.add(new TableValidationProblem(
                                "TABLE_REFERENCE_DEPTH_EXCEEDED", basePath,
                                "Maximum reference depth of " + MAX_REFERENCE_DEPTH + " exceeded"));
                        continue;
                    }

                    if (visiting.contains(ref.targetId())) {
                        problems.add(new TableValidationProblem(
                                "TABLE_REFERENCE_CYCLE", basePath,
                                "Cycle detected at table " + ref.targetId()));
                        continue;
                    }

                    if (currentTableId != null && ref.targetId().equals(currentTableId)) {
                        problems.add(new TableValidationProblem(
                                "TABLE_REFERENCE_CYCLE", basePath,
                                "Self-referential cycle detected"));
                        continue;
                    }
                }
            }
        }
    }

    private record EntryWithIndex(RollableTableEntryWrite entry, int index) {}
}
