package dev.hendrikhoemberg.dmhelper.audio.service;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference;
import dev.hendrikhoemberg.dmhelper.audio.provider.AudioProviderRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class AudioCueValidator {

    static final Pattern CUE_KEY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,99}$");
    static final int MAX_CACHED_TITLE = 500;
    static final int MAX_ARTIST_OWNER = 500;
    static final int MAX_ARTWORK_URL = 2000;
    static final int MAX_NOTES = 10000;
    static final int MIN_VOLUME = 0;
    static final int MAX_VOLUME = 100;
    static final int MIN_DURATION = 1;

    private final AudioCueRepository audioCueRepository;

    public AudioCueValidator(AudioCueRepository audioCueRepository) {
        this.audioCueRepository = audioCueRepository;
    }

    public void validate(AudioCueWrite write, UUID campaignIdOrNull) {
        List<AudioCueValidationProblem> problems = collectProblems(write, campaignIdOrNull);
        if (!problems.isEmpty()) {
            throw new AudioCueValidationException(problems);
        }
    }

    public List<AudioCueValidationProblem> collectProblems(AudioCueWrite write, UUID campaignIdOrNull) {
        List<AudioCueValidationProblem> problems = new ArrayList<>();
        if (write == null) {
            problems.add(problem("CUE_FIELD_REQUIRED", "/", "Write object is required"));
            return problems;
        }

        validateCueKey(write.cueKey(), problems);
        validateName(write.name(), problems);
        validateProviderId(write.providerId(), problems);
        validateReference(write.referenceKind(), write.providerReference(), problems);
        validateReferenceParseable(write.providerId(), write.providerReference(), problems);
        validateCategory(write.category(), problems);
        validateTransitionPreference(write.transitionPreference(), problems);
        validateVolume(write.volumeHint(), problems);
        validateDuration(write.durationSeconds(), problems);
        validateMetadataLength(write.cachedTitle(), write.artistOrOwner(), write.artworkUrl(), write.notes(), problems);
        validateDuplicateCueKey(write.cueKey(), campaignIdOrNull, write.id(), problems);

        return problems;
    }

    private void validateCueKey(String cueKey, List<AudioCueValidationProblem> problems) {
        if (cueKey == null || cueKey.isBlank()) {
            problems.add(problem("CUE_FIELD_REQUIRED", "/cueKey", "Cue key is required"));
        } else if (!CUE_KEY_PATTERN.matcher(cueKey).matches()) {
            problems.add(problem("INVALID_CUE_KEY", "/cueKey",
                    "Cue key must match " + CUE_KEY_PATTERN.pattern()));
        }
    }

    private void validateName(String name, List<AudioCueValidationProblem> problems) {
        if (name == null || name.isBlank()) {
            problems.add(problem("CUE_FIELD_REQUIRED", "/name", "Name is required"));
        }
    }

    private void validateProviderId(String providerId, List<AudioCueValidationProblem> problems) {
        if (providerId != null && !providerId.isBlank()) {
            boolean known = AudioProviderRegistry.all().stream()
                    .anyMatch(a -> a.id().id().equals(providerId));
            if (!known) {
                problems.add(problem("UNKNOWN_PROVIDER", "/providerId", "Unknown provider: " + providerId));
            }
        }
    }

    private void validateReference(AudioReferenceKind referenceKind, String providerReference,
                                   List<AudioCueValidationProblem> problems) {
        if (referenceKind == null) {
            problems.add(problem("CUE_FIELD_REQUIRED", "/referenceKind", "Reference kind is required"));
        }
        if (providerReference == null || providerReference.isBlank()) {
            problems.add(problem("INVALID_REFERENCE", "/providerReference", "Provider reference is required"));
        }
    }

    private void validateReferenceParseable(String providerId, String providerReference,
                                            List<AudioCueValidationProblem> problems) {
        if (providerReference == null || providerReference.isBlank()) {
            return;
        }
        if (providerId == null || providerId.isBlank()) {
            return;
        }
        try {
            var adapter = AudioProviderRegistry.lookup(providerId);
            adapter.parseReference(providerReference);
        } catch (Exception e) {
            problems.add(problem("INVALID_REFERENCE", "/providerReference",
                    "Provider reference could not be parsed: " + e.getMessage()));
        }
    }

    private void validateDuplicateCueKey(String cueKey, UUID campaignIdOrNull, UUID excludeCueId,
                                         List<AudioCueValidationProblem> problems) {
        if (cueKey == null || cueKey.isBlank()) {
            return;
        }
        if (campaignIdOrNull == null) {
            return;
        }
        var existing = audioCueRepository.findByCampaignIdAndCueKey(campaignIdOrNull, cueKey);
        if (existing.isPresent() && !existing.get().getId().equals(excludeCueId)) {
            problems.add(problem("DUPLICATE_CUE_KEY", "/cueKey",
                    "Cue key '" + cueKey + "' already exists in this campaign"));
        }
    }

    private void validateCategory(AudioCategory category, List<AudioCueValidationProblem> problems) {
        if (category == null) {
            problems.add(problem("CUE_FIELD_REQUIRED", "/category", "Category is required"));
        }
    }

    private void validateTransitionPreference(AudioTransitionPreference transitionPreference,
                                              List<AudioCueValidationProblem> problems) {
        if (transitionPreference == null) {
            problems.add(problem("CUE_FIELD_REQUIRED", "/transitionPreference", "Transition preference is required"));
        }
    }

    private void validateVolume(Integer volumeHint, List<AudioCueValidationProblem> problems) {
        if (volumeHint != null && (volumeHint < MIN_VOLUME || volumeHint > MAX_VOLUME)) {
            problems.add(problem("VOLUME_OUT_OF_BOUNDS", "/volumeHint",
                    "Volume must be between " + MIN_VOLUME + " and " + MAX_VOLUME));
        }
    }

    private void validateDuration(Integer durationSeconds, List<AudioCueValidationProblem> problems) {
        if (durationSeconds != null && durationSeconds < MIN_DURATION) {
            problems.add(problem("DURATION_OUT_OF_BOUNDS", "/durationSeconds",
                    "Duration must be at least " + MIN_DURATION));
        }
    }

    private void validateMetadataLength(String cachedTitle, String artistOrOwner, String artworkUrl,
                                        String notes, List<AudioCueValidationProblem> problems) {
        if (cachedTitle != null && cachedTitle.length() > MAX_CACHED_TITLE) {
            problems.add(problem("METADATA_TOO_LONG", "/cachedTitle",
                    "Cached title must not exceed " + MAX_CACHED_TITLE + " characters"));
        }
        if (artistOrOwner != null && artistOrOwner.length() > MAX_ARTIST_OWNER) {
            problems.add(problem("METADATA_TOO_LONG", "/artistOrOwner",
                    "Artist/owner must not exceed " + MAX_ARTIST_OWNER + " characters"));
        }
        if (artworkUrl != null && artworkUrl.length() > MAX_ARTWORK_URL) {
            problems.add(problem("METADATA_TOO_LONG", "/artworkUrl",
                    "Artwork URL must not exceed " + MAX_ARTWORK_URL + " characters"));
        }
        if (notes != null && notes.length() > MAX_NOTES) {
            problems.add(problem("METADATA_TOO_LONG", "/notes",
                    "Notes must not exceed " + MAX_NOTES + " characters"));
        }
    }

    private AudioCueValidationProblem problem(String code, String path, String message) {
        return new AudioCueValidationProblem(code, path, message);
    }
}
