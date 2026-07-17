package dev.hendrikhoemberg.dmhelper.agent;

import java.util.List;
import java.util.Map;

public record CapabilityManifest(
        int manifestVersion,
        String catalogVersion,
        List<Integer> packageFormatVersions,
        List<String> rulesets,
        Map<String, String> endpoints,
        List<Capability> capabilities,
        List<String> contentTypes,
        List<String> flagshipFixtures
) {
    public record Capability(
            String id,
            String name,
            String status,
            Integer deliveryItem,
            String notes
    ) {}
}
