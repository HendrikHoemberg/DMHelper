package dev.hendrikhoemberg.dmhelper.session.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CockpitLayoutPresetRepository extends JpaRepository<CockpitLayoutPreset, UUID> {
    List<CockpitLayoutPreset> findAllByOrderByNormalizedNameAsc();
    boolean existsByNormalizedName(String normalizedName);
    boolean existsByNormalizedNameAndIdNot(String normalizedName, UUID id);
}
