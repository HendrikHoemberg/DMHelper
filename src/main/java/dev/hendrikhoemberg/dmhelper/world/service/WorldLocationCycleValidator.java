package dev.hendrikhoemberg.dmhelper.world.service;

import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class WorldLocationCycleValidator {

    private final WorldLocationRepository locationRepository;

    public WorldLocationCycleValidator(WorldLocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public void assertNoCycle(UUID locationId, UUID proposedParentId) {
        if (proposedParentId == null) return;
        if (proposedParentId.equals(locationId)) {
            throw new IllegalArgumentException("Location parent chain contains a cycle");
        }
        Set<UUID> visited = new HashSet<>();
        UUID current = proposedParentId;
        while (current != null) {
            if (!visited.add(current)) {
                throw new IllegalArgumentException("Location parent chain contains a cycle");
            }
            if (current.equals(locationId)) {
                throw new IllegalArgumentException("Location parent chain contains a cycle");
            }
            var parent = locationRepository.findById(current).orElse(null);
            current = parent != null && parent.getParentLocation() != null
                    ? parent.getParentLocation().getId() : null;
        }
    }
}
