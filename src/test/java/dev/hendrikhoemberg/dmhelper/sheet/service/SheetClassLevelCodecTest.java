package dev.hendrikhoemberg.dmhelper.sheet.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SheetClassLevelCodecTest {

    @Test
    void readsCanonicalClassSourceKey() {
        String json = """
                [{"classSourceKey":"srd-2024_fighter","level":3,"hitDieRolls":[8,6]}]
                """;
        var levels = SheetClassLevelCodec.read(json);
        assertThat(levels).hasSize(1);
        assertThat(levels.get(0).classSourceKey()).isEqualTo("srd-2024_fighter");
        assertThat(levels.get(0).level()).isEqualTo(3);
    }

    @Test
    void readsLegacyInternalClassRefString() {
        String json = """
                [{"classRef":"srd-2024_rogue","level":5,"hitDieRolls":[8,5,6,7,4]}]
                """;
        var levels = SheetClassLevelCodec.read(json);
        assertThat(levels.get(0).classSourceKey()).isEqualTo("srd-2024_rogue");
    }

    @Test
    void writeUsesOnlyClassSourceKey() {
        String out = SheetClassLevelCodec.write(List.of(
                new SheetService.ClassLevelEntry("srd-2024_wizard", 2, List.of(4), null)));
        assertThat(out).contains("classSourceKey");
        assertThat(out).doesNotContain("\"classRef\"");
    }

    @Test
    void readsSubclassSourceKey() {
        String json = """
                [{"classSourceKey":"srd-2024_fighter","subclassSourceKey":"srd-2024_fighter_champion","level":3,"hitDieRolls":[8,6]}]
                """;
        var levels = SheetClassLevelCodec.read(json);
        assertThat(levels.get(0).classSourceKey()).isEqualTo("srd-2024_fighter");
        assertThat(levels.get(0).subclassSourceKey()).isEqualTo("srd-2024_fighter_champion");
    }

    @Test
    void writeIncludesSubclassSourceKeyWhenPresent() {
        String out = SheetClassLevelCodec.write(List.of(
                new SheetService.ClassLevelEntry("srd-2024_fighter", 3, List.of(8, 6), "srd-2024_fighter_champion")));
        assertThat(out).contains("subclassSourceKey");
        assertThat(out).contains("srd-2024_fighter_champion");
    }

    @Test
    void writeOmitsSubclassSourceKeyWhenNull() {
        String out = SheetClassLevelCodec.write(List.of(
                new SheetService.ClassLevelEntry("srd-2024_fighter", 3, List.of(8, 6), null)));
        assertThat(out).doesNotContain("subclassSourceKey");
    }
}
