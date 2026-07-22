package dev.hendrikhoemberg.dmhelper.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceProfileParityTest {

    @Test
    void integrationTestsDisableOpenInViewLikeProduction() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Path.of("src/test/resources/application.properties"))) {
            properties.load(reader);
        }

        assertThat(properties.getProperty("spring.jpa.open-in-view"))
                .as("controller integration tests must expose detached-entity failures")
                .isEqualTo("false");
    }
}
