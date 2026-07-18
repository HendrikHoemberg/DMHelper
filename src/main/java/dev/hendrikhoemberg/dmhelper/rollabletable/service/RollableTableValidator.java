package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.dice.DiceExpressionSpec;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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

    private final RollableTableRepository rollableTableRepository;
    private final TableReferenceResolver referenceResolver;

    @Autowired
    public RollableTableValidator(RollableTableRepository rollableTableRepository,
                                   TableReferenceResolver referenceResolver) {
        this.rollableTableRepository = rollableTableRepository;
        this.referenceResolver = referenceResolver;
    }

    public RollableTableValidator() {
        this.rollableTableRepository = null;
        this.referenceResolver = null;
    }

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
        validateReferenceResolution(write, currentTableId, problems);
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

    private void validateReferenceResolution(RollableTableWrite write, UUID currentTableId,
                                              List<TableValidationProblem> problems) {
        if (referenceResolver == null) return;
        if (write.entries() == null) return;
        for (int ei = 0; ei < write.entries().size(); ei++) {
            var entry = write.entries().get(ei);
            if (entry.references() == null) continue;
            for (int ri = 0; ri < entry.references().size(); ri++) {
                var ref = entry.references().get(ri);
                String basePath = "/entries/" + ei + "/references/" + ri;
                try {
                    referenceResolver.require(null, ref);
                } catch (IllegalArgumentException e) {
                    String msg = e.getMessage();
                    if (msg != null && msg.startsWith("UNRESOLVED_REFERENCE")) {
                        problems.add(new TableValidationProblem(
                                "UNRESOLVED_REFERENCE", basePath, msg));
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    private void validateGraph(RollableTableWrite write, UUID currentTableId, List<TableValidationProblem> problems) {
        List<TableValidationProblem> graphProblems = new ArrayList<>();
        var visited = new HashSet<UUID>();
        var visiting = new HashSet<UUID>();
        var path = new ArrayList<PathEntry>();
        var knownTables = new java.util.HashMap<UUID, RollableTableWrite>();
        if (currentTableId != null) {
            knownTables.put(currentTableId, write);
        }
        dfsValidate(write, currentTableId, visiting, path, 0, graphProblems, knownTables);
        problems.addAll(graphProblems);
    }

    private void dfsValidate(RollableTableWrite write, UUID currentTableId,
                             Set<UUID> visiting, List<PathEntry> path, int depth,
                             List<TableValidationProblem> problems,
                             java.util.Map<UUID, RollableTableWrite> knownTables) {
        if (write.entries() == null) return;
        for (int ei = 0; ei < write.entries().size(); ei++) {
            var entry = write.entries().get(ei);
            if (entry.references() == null) continue;
            for (int ri = 0; ri < entry.references().size(); ri++) {
                var ref = entry.references().get(ri);
                if (ref.scope() != TableReferenceScope.ENTITY
                        || ref.targetType() != CampaignContentType.ROLLABLE_TABLE
                        || ref.targetId() == null) {
                    continue;
                }
                String basePath = "/entries/" + ei + "/references/" + ri;

                if (depth >= MAX_REFERENCE_DEPTH) {
                    problems.add(new TableValidationProblem(
                            "TABLE_REFERENCE_DEPTH_EXCEEDED", basePath,
                            "Maximum reference depth of " + MAX_REFERENCE_DEPTH + " exceeded at path: "
                                    + formatPath(path)));
                    continue;
                }

                if (visiting.contains(ref.targetId())) {
                    problems.add(new TableValidationProblem(
                            "TABLE_REFERENCE_CYCLE", basePath,
                            "Cycle detected at table " + ref.targetId() + " via path: "
                                    + formatPath(path)));
                    continue;
                }

                if (currentTableId != null && ref.targetId().equals(currentTableId)) {
                    problems.add(new TableValidationProblem(
                            "TABLE_REFERENCE_CYCLE", basePath,
                            "Self-referential cycle detected"));
                    continue;
                }

                if (rollableTableRepository != null) {
                    RollableTableWrite targetWrite = knownTables.get(ref.targetId());
                    if (targetWrite == null) {
                        Optional<RollableTable> targetEntity = rollableTableRepository.findWithEntriesById(ref.targetId());
                        if (targetEntity.isPresent()) {
                            targetWrite = toWrite(targetEntity.get());
                            knownTables.put(ref.targetId(), targetWrite);
                        }
                    }

                    if (targetWrite != null) {
                        visiting.add(ref.targetId());
                        path.add(new PathEntry(ref.targetId(), ei, ri));
                        dfsValidate(targetWrite, currentTableId, visiting, path, depth + 1,
                                problems, knownTables);
                        path.removeLast();
                        visiting.remove(ref.targetId());
                    }
                }
            }
        }
    }

    private String formatPath(List<PathEntry> path) {
        if (path.isEmpty()) return "(root)";
        var sb = new StringBuilder();
        for (var entry : path) {
            if (!sb.isEmpty()) sb.append(" -> ");
            sb.append("table:").append(entry.tableId)
                    .append("[e").append(entry.entryIndex)
                    .append("/r").append(entry.refIndex).append("]");
        }
        return sb.toString();
    }

    private RollableTableWrite toWrite(RollableTable entity) {
        var entries = new ArrayList<RollableTableEntryWrite>();
        for (var e : entity.getEntries()) {
            var refs = new ArrayList<RollableTableReferenceWrite>();
            for (var r : e.getReferences()) {
                refs.add(new RollableTableReferenceWrite(
                        r.getTargetScope(),
                        CampaignContentType.valueOf(r.getTargetType()),
                        r.getTargetId(), r.getCatalogRuleset(),
                        r.getCatalogSourceKey(), r.getDisplayText()));
            }
            entries.add(new RollableTableEntryWrite(e.getEntryKey(),
                    e.getRangeStart(), e.getRangeEnd(), e.getWeight(),
                    e.getResultText(), e.getQuantityExpression(), refs));
        }
        return new RollableTableWrite(entity.getSourceKey(), entity.getName(),
                entity.getDescription(), entity.getAddressMode(),
                entity.getRollExpression(), entity.getCategory(),
                entity.getTags() != null ? List.of(entity.getTags().split(",\s*")) : List.of(),
                entries);
    }

    private record EntryWithIndex(RollableTableEntryWrite entry, int index) {}
    private record PathEntry(UUID tableId, int entryIndex, int refIndex) {}
}
