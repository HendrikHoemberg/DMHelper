package dev.hendrikhoemberg.dmhelper.audio.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;

class AudioCredentialBoundaryTest {

    @TempDir
    Path isolatedHome;

    @Test
    void clearCredentialsDoesNotCreateProviderDirectory() {
        AudioProviderAdapter adapter = AudioProviderRegistry.lookup(AudioProviderId.YOUTUBE);
        Path providersDir = isolatedHome.resolve(".dmhelper").resolve("providers");
        adapter.clearCredentials();
        assertThat(providersDir).doesNotExist();
    }

    @Test
    void clearCredentialsDoesNotModifyExistingFiles() throws IOException {
        Path dmhelperDir = Files.createDirectories(isolatedHome.resolve(".dmhelper"));
        Path someFile = Files.createFile(dmhelperDir.resolve("config.txt"));
        Files.writeString(someFile, "hello");
        FileTime originalTime = Files.getLastModifiedTime(someFile);

        AudioProviderAdapter adapter = AudioProviderRegistry.lookup(AudioProviderId.YOUTUBE);
        adapter.clearCredentials();

        assertThat(someFile).exists();
        assertThat(Files.readString(someFile)).isEqualTo("hello");
        assertThat(Files.getLastModifiedTime(someFile)).isEqualTo(originalTime);
    }

    @Test
    void clearCredentialsDoesNotCreateAnyFiles() throws IOException {
        AudioProviderAdapter adapter = AudioProviderRegistry.lookup(AudioProviderId.YOUTUBE);
        Path dmhelperDir = isolatedHome.resolve(".dmhelper");
        adapter.clearCredentials();
        assertThat(dmhelperDir).doesNotExist();
    }
}
