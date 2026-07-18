package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RollableTableValidatorTest {

    private final RollableTableValidator validator = new RollableTableValidator();

    @Test
    void invalidTableExpression() {
        var write = new RollableTableWrite(
                "test", "Test", null, TableAddressMode.RANGE,
                "not-a-dice", TableCategory.GENERIC, List.of(),
                List.of(entry("a", 1, 6, null, "Ok", null, List.of())));
        assertProblem(write, "INVALID_TABLE_EXPRESSION", "/rollExpression");
    }

    @Test
    void rangeGap() {
        var write = new RollableTableWrite(
                "test", "Test", null, TableAddressMode.RANGE,
                "1d20", TableCategory.GENERIC, List.of(),
                List.of(
                        entry("a", 1, 10, null, "A", null, List.of()),
                        entry("b", 12, 20, null, "B", null, List.of())));
        assertProblem(write, "TABLE_RANGE_GAP", "/entries/1/rangeStart");
    }

    @Test
    void rangeOverlap() {
        var write = new RollableTableWrite(
                "test", "Test", null, TableAddressMode.RANGE,
                "1d20", TableCategory.GENERIC, List.of(),
                List.of(
                        entry("a", 1, 10, null, "A", null, List.of()),
                        entry("b", 10, 20, null, "B", null, List.of())));
        assertProblem(write, "TABLE_RANGE_OVERLAP", "/entries/1/rangeStart");
    }

    @Test
    void rangeBounds() {
        var write = new RollableTableWrite(
                "test", "Test", null, TableAddressMode.RANGE,
                "2d6", TableCategory.GENERIC, List.of(),
                List.of(entry("a", 3, 6, null, "A", null, List.of()),
                        entry("b", 7, 12, null, "B", null, List.of())));
        assertProblem(write, "TABLE_RANGE_BOUNDS", "/entries/0/rangeStart");
    }

    @Test
    void invalidWeight() {
        var write = new RollableTableWrite(
                "test", "Test", null, TableAddressMode.WEIGHTED,
                null, TableCategory.GENERIC, List.of(),
                List.of(
                        entry("a", null, null, 0, "A", null, List.of()),
                        entry("b", null, null, 2, "B", null, List.of())));
        assertProblem(write, "TABLE_WEIGHT_INVALID", "/entries/0/weight");
    }

    @Test
    void invalidQuantityExpression() {
        var write = new RollableTableWrite(
                "test", "Test", null, TableAddressMode.WEIGHTED,
                null, TableCategory.GENERIC, List.of(),
                List.of(
                        entry("a", null, null, 1, "A", "not-a-dice", List.of()),
                        entry("b", null, null, 2, "B", null, List.of())));
        assertProblem(write, "INVALID_QUANTITY_EXPRESSION", "/entries/0/quantityExpression");
    }

    @Test
    void invalidReferenceType() {
        var write = new RollableTableWrite(
                "test", "Test", null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of(),
                List.of(entry("ref", 1, 6, null, null, null,
                        List.of(new RollableTableReferenceWrite(
                                TableReferenceScope.ENTITY, CampaignContentType.SESSION,
                                UUID.randomUUID(), null, null, "Bad")))));
        assertProblem(write, "INVALID_TABLE_REFERENCE_TYPE", "/entries/0/references/0");
    }

    @Test
    void valid2d6RangeTable() {
        var write = new RollableTableWrite(
                "test-2d6", "2d6 Table", null, TableAddressMode.RANGE,
                "2d6", TableCategory.GENERIC, List.of(),
                List.of(
                        entry("two", 2, 3, null, "Low", null, List.of()),
                        entry("mid", 4, 10, null, "Mid", null, List.of()),
                        entry("high", 11, 12, null, "High", null, List.of())));
        assertThatCode(() -> validator.validate(write, null)).doesNotThrowAnyException();
    }

    @Test
    void normalizedWeightedTable() {
        var write = new RollableTableWrite(
                "test-weighted", "Weighted", null, TableAddressMode.WEIGHTED,
                null, TableCategory.GENERIC, List.of(),
                List.of(
                        entry("a", null, null, 3, "A", null, List.of()),
                        entry("b", null, null, 2, "B", null, List.of())));
        assertThatCode(() -> validator.validate(write, null)).doesNotThrowAnyException();
    }

    @Test
    void detectsSelfReferenceCycle() {
        UUID tableId = UUID.randomUUID();
        var refToSelf = new RollableTableReferenceWrite(
                TableReferenceScope.ENTITY, CampaignContentType.ROLLABLE_TABLE,
                tableId, null, null, "Self");
        var write = new RollableTableWrite(
                "self-cycle", "Self Cycle", null, TableAddressMode.RANGE,
                "1d6", TableCategory.GENERIC, List.of(),
                List.of(entry("ref", 1, 6, null, null, null, List.of(refToSelf))));

        assertThatThrownBy(() -> validator.validate(write, tableId))
                .isInstanceOf(RollableTableValidationException.class)
                .satisfies(e -> {
                    var ex = (RollableTableValidationException) e;
                    assertThat(ex.problems()).anyMatch(p ->
                            p.code().equals("TABLE_REFERENCE_CYCLE"));
                });
    }

    private void assertProblem(RollableTableWrite write, String expectedCode, String expectedPath) {
        assertThatThrownBy(() -> validator.validate(write, null))
                .isInstanceOf(RollableTableValidationException.class)
                .satisfies(e -> {
                    var ex = (RollableTableValidationException) e;
                    assertThat(ex.problems()).anyMatch(p ->
                            p.code().equals(expectedCode) && p.path().equals(expectedPath));
                });
    }

    private static RollableTableEntryWrite entry(String key, Integer rs, Integer re, Integer w,
                                                  String text, String qty,
                                                  List<RollableTableReferenceWrite> refs) {
        return new RollableTableEntryWrite(key, rs, re, w, text, qty,
                refs != null ? refs : List.of());
    }
}
