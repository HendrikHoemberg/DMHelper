package dev.hendrikhoemberg.dmhelper.common.config;

import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SpellReseedMigrationTest {

    @Autowired private SpellRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    private Spell spell(String key, String school) {
        Spell s = new Spell();
        s.setSourceKey(key);
        s.setName(key);
        s.setLevel(1);
        s.setSchool(school);
        return s;
    }

    @Test
    void clearsStaleSpellsWithNullSchool() {
        repository.save(spell("a", null));
        repository.save(spell("b", null));
        new SpellReseedMigration(repository).reseedIfStale();
        assertThat(repository.count()).isZero();
    }

    @Test
    void clearsStaleSpellsWithNoSchool() {
        repository.save(spell("a", ""));
        repository.save(spell("b", ""));
        new SpellReseedMigration(repository).reseedIfStale();
        assertThat(repository.count()).isZero();
    }

    @Test
    void keepsHealthySpellsThatHaveSchools() {
        repository.save(spell("a", "Evocation"));
        new SpellReseedMigration(repository).reseedIfStale();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void noOpOnEmptyDatabase() {
        new SpellReseedMigration(repository).reseedIfStale();
        assertThat(repository.count()).isZero();
    }
}
