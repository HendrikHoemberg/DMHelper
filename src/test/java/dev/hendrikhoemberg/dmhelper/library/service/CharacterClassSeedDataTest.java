package dev.hendrikhoemberg.dmhelper.library.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CharacterClassSeedDataTest {

    @Test
    void jsonContainsOnlyBaseClasses() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = new ClassPathResource("srd/srd-5.2-classes.json").getInputStream()) {
            JsonNode root = mapper.readTree(is);
            JsonNode results = root.get("results");
            assertEquals(12, results.size(), "expected only the 12 base classes");
            for (JsonNode entry : results) {
                assertFalse(entry.path("subclass_of").isObject(),
                        "subclass leaked into class list: " + entry.path("name").asText());
            }
        }
    }
}
