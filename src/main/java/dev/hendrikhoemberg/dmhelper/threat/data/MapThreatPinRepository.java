package dev.hendrikhoemberg.dmhelper.threat.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MapThreatPinRepository extends JpaRepository<MapThreatPin, UUID> {

    List<MapThreatPin> findByMapIdOrderBySortOrderAsc(UUID mapId);

    List<MapThreatPin> findByThreatKindAndThreatId(ThreatKind threatKind, UUID threatId);

    Optional<MapThreatPin> findByMapIdAndPinKey(UUID mapId, String pinKey);

    void deleteByThreatKindAndThreatId(ThreatKind threatKind, UUID threatId);
}
