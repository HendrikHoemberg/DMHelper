package dev.hendrikhoemberg.dmhelper.session.data;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CockpitLayoutPresetRepositoryTest {
    @Autowired CockpitLayoutPresetRepository repository;

    @Test
    void ordersByNormalizedNameAndAdvancesTheOptimisticVersion() {
        CockpitLayoutPreset zed = preset("Zed", "zed");
        CockpitLayoutPreset alpha = preset("Alpha", "alpha");
        repository.saveAndFlush(zed);
        repository.saveAndFlush(alpha);

        assertThat(repository.findAllByOrderByNormalizedNameAsc())
                .extracting(CockpitLayoutPreset::getName)
                .containsExactly("Alpha", "Zed");

        long before = zed.getVersion();
        zed.setName("Zed table");
        repository.saveAndFlush(zed);
        assertThat(zed.getVersion()).isEqualTo(before + 1);
    }

    @Test
    void normalizedNameIsUnique() {
        repository.saveAndFlush(preset("Combat", "combat"));
        assertThatThrownBy(() -> repository.saveAndFlush(preset("COMBAT", "combat")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CockpitLayoutPreset preset(String name, String normalized) {
        CockpitLayoutPreset entity = new CockpitLayoutPreset();
        entity.setName(name);
        entity.setNormalizedName(normalized);
        entity.setLayoutSchemaVersion(1);
        entity.setLayoutJson("{\"schemaVersion\":1}");
        return entity;
    }
}
