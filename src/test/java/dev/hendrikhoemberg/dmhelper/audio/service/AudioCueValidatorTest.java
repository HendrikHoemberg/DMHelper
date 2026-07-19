package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AudioCueValidatorTest {

    private final AudioCueRepository repository = mock(AudioCueRepository.class);
    private final AudioCueValidator validator = new AudioCueValidator(repository);

    @BeforeEach
    void setUp() {
        when(repository.findByCampaignIdAndCueKey(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void rejectsNullWrite() {
        assertProblem(null, "CUE_FIELD_REQUIRED", "/");
    }

    @Test
    void rejectsBlankCueKey() {
        AudioCueWrite write = validBase();
        assertProblem(write.withCueKey(" "), "CUE_FIELD_REQUIRED", "/cueKey");
    }

    @Test
    void rejectsInvalidCueKeyPattern() {
        AudioCueWrite write = validBase();
        assertProblem(write.withCueKey("Bad Key!"), "INVALID_CUE_KEY", "/cueKey");
        assertProblem(write.withCueKey(""), "CUE_FIELD_REQUIRED", "/cueKey");
        assertProblem(write.withCueKey("a" + "x".repeat(100)), "INVALID_CUE_KEY", "/cueKey");
    }

    @Test
    void rejectsBlankName() {
        AudioCueWrite write = validBase();
        assertProblem(write.withName(" "), "CUE_FIELD_REQUIRED", "/name");
    }

    @Test
    void rejectsUnknownProviderId() {
        AudioCueWrite write = validBase();
        assertProblem(write.withProviderId("nonexistent-provider"), "UNKNOWN_PROVIDER", "/providerId");
    }

    @Test
    void rejectsInvalidReferenceInput() {
        AudioCueWrite write = validBase();
        assertProblem(write.withProviderReference(""), "INVALID_REFERENCE", "/providerReference");
        assertProblem(write.withProviderReference("   "), "INVALID_REFERENCE", "/providerReference");
    }

    @Test
    void rejectsNullReferenceKind() {
        AudioCueWrite write = validBase();
        assertProblem(write.withReferenceKind(null), "CUE_FIELD_REQUIRED", "/referenceKind");
    }

    @Test
    void rejectsNullCategory() {
        AudioCueWrite write = validBase();
        assertProblem(write.withCategory(null), "CUE_FIELD_REQUIRED", "/category");
    }

    @Test
    void rejectsNullTransitionPreference() {
        AudioCueWrite write = validBase();
        assertProblem(write.withTransitionPreference(null), "CUE_FIELD_REQUIRED", "/transitionPreference");
    }

    @Test
    void rejectsVolumeOutOfBounds() {
        AudioCueWrite write = validBase();
        assertProblem(write.withVolumeHint(-1), "VOLUME_OUT_OF_BOUNDS", "/volumeHint");
        assertProblem(write.withVolumeHint(101), "VOLUME_OUT_OF_BOUNDS", "/volumeHint");
    }

    @Test
    void acceptsVolumeNull() {
        AudioCueWrite write = validBase().withVolumeHint(null);
        assertThat(validator.collectProblems(write, null)).isEmpty();
    }

    @Test
    void acceptsVolumeInBounds() {
        AudioCueWrite write = validBase().withVolumeHint(50);
        assertThat(validator.collectProblems(write, null)).isEmpty();
    }

    @Test
    void rejectsDurationOutOfBounds() {
        AudioCueWrite write = validBase();
        assertProblem(write.withDurationSeconds(-1), "DURATION_OUT_OF_BOUNDS", "/durationSeconds");
        assertProblem(write.withDurationSeconds(0), "DURATION_OUT_OF_BOUNDS", "/durationSeconds");
    }

    @Test
    void acceptsDurationNull() {
        AudioCueWrite write = validBase().withDurationSeconds(null);
        assertThat(validator.collectProblems(write, null)).isEmpty();
    }

    @Test
    void acceptsDurationInBounds() {
        AudioCueWrite write = validBase().withDurationSeconds(300);
        assertThat(validator.collectProblems(write, null)).isEmpty();
    }

    @Test
    void rejectsEmptyNotesMetadata() {
        AudioCueWrite write = validBase().withCachedTitle("").withArtistOrOwner("").withArtworkUrl("")
                .withNotes("");
        assertThat(validator.collectProblems(write, null)).isEmpty();
    }

    @Test
    void rejectsOverlongMetadata() {
        AudioCueWrite write = validBase()
                .withCachedTitle("x".repeat(501))
                .withArtistOrOwner("x".repeat(501))
                .withArtworkUrl("x".repeat(2001))
                .withNotes("x".repeat(10001));
        assertProblem(write, "METADATA_TOO_LONG", "/cachedTitle");
        assertProblem(write, "METADATA_TOO_LONG", "/artistOrOwner");
        assertProblem(write, "METADATA_TOO_LONG", "/artworkUrl");
        assertProblem(write, "METADATA_TOO_LONG", "/notes");
    }

    @Test
    void acceptsValidCue() {
        AudioCueWrite write = validBase();
        assertThat(validator.collectProblems(write, null)).isEmpty();
    }

    private void assertProblem(AudioCueWrite write, String code, String path) {
        assertThatThrownBy(() -> validator.validate(write, null))
                .isInstanceOfSatisfying(AudioCueValidationException.class, ex ->
                        assertThat(ex.problems())
                                .anyMatch(p -> p.code().equals(code) && p.path().equals(path)));
    }

    private void assertProblem(AudioCueWrite write, String code) {
        assertThatThrownBy(() -> validator.validate(write, null))
                .isInstanceOfSatisfying(AudioCueValidationException.class, ex ->
                        assertThat(ex.problems())
                                .anyMatch(p -> p.code().equals(code)));
    }

    private AudioCueWrite validBase() {
        return new AudioCueWrite(
                "ambient-cave", "Cave Ambience", "youtube",
                dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind.VIDEO,
                "dQw4w9WgXcQ", "Rick Astley - Never Gonna Give You Up",
                "Rick Astley", "https://example.com/art.jpg", 212,
                AudioCategory.AMBIENT, 80, AudioTransitionPreference.CROSSFADE,
                "Some notes", null);
    }
}
