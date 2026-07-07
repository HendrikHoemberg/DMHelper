package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({SpellSeedService.class, SrdSeedService.class})
class SpellSeedServiceTest {

    @Autowired private SpellRepository repository;
    @Autowired private SpellSeedService service;

    @BeforeEach
    void seed() {
        service.seedIfEmpty();
    }

    @Test
    void shouldSeedAllSpellsWithASchool() {
        assertThat(repository.count()).isEqualTo(339);
        long blankSchool = repository.findAll().stream()
                .filter(s -> s.getSchool() == null || s.getSchool().isBlank())
                .count();
        assertThat(blankSchool).isZero();
    }

    @Test
    void shouldPopulateFireballFields() {
        Spell fireball = repository.findAll().stream()
                .filter(s -> s.getName().equals("Fireball"))
                .findFirst().orElseThrow();
        assertThat(fireball.getSchool()).isEqualTo("Evocation");
        assertThat(fireball.getComponents()).contains("V").contains("S").contains("M");
        assertThat(fireball.getHigherLevel()).isNotBlank();
        assertThat(fireball.isConcentration()).isFalse();
    }

    @Test
    void shouldHaveConcentrationSpells() {
        long concentrationCount = repository.findAll().stream()
                .filter(Spell::isConcentration)
                .count();
        assertThat(concentrationCount).isGreaterThan(0);
    }
}
