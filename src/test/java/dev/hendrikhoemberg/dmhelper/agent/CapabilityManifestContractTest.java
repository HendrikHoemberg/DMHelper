package dev.hendrikhoemberg.dmhelper.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CapabilityManifestContractTest {

    @Autowired
    AgentContractService service;

    @Test
    void statusesAreClosedEnum() {
        assertThat(service.capabilities().capabilities())
                .allMatch(c -> Set.of("SUPPORTED", "PARTIAL", "UNSUPPORTED", "EXPERIMENTAL")
                        .contains(c.status()));
    }

    @Test
    void requiredReadinessCapabilitiesPresent() {
        var ids = service.capabilities().capabilities().stream()
                .map(CapabilityManifest.Capability::id)
                .collect(Collectors.toSet());
        assertThat(ids).contains(
                "p0.runtime_reliability",
                "package.v1.contract",
                "package.v2.foundation",
                "package.v2.round_trip",
                "session.cockpit",
                "adventure.structured_scenes",
                "quest.objectives",
                "compendium.custom_with_provenance",
                "character.sheet_completion",
                "encounter.prep_and_waves",
                "map.published_workflow",
                "agent.sdk",
                "world.graph",
                "map.fog_of_war"
        );
    }

    @Test
    void p3ItemsAreNotFalselySupported() {
        var byId = service.capabilities().capabilities().stream()
                .collect(Collectors.toMap(CapabilityManifest.Capability::id, c -> c));
        assertThat(byId.get("world.graph").status()).isEqualTo("UNSUPPORTED");
        assertThat(byId.get("map.fog_of_war").status()).isEqualTo("UNSUPPORTED");
        assertThat(byId.get("agent.sdk").status()).isIn("PARTIAL", "SUPPORTED");
    }

    @Test
    void flagshipFixturesExistOnClasspath() {
        for (String path : service.capabilities().flagshipFixtures()) {
            String resource = path.startsWith("classpath:") ? path.substring("classpath:".length()) : path;
            assertThat(new ClassPathResource(resource).exists())
                    .as("missing fixture %s", path)
                    .isTrue();
        }
    }
}
