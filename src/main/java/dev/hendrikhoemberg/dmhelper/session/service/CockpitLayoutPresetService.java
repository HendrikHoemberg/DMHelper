package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.session.data.CockpitLayoutPreset;
import dev.hendrikhoemberg.dmhelper.session.data.CockpitLayoutPresetRepository;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitBuiltInPresetCatalog;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitLayoutCodec;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitLayoutDocument;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitLayoutResolver;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitLayoutValidator;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CockpitLayoutPresetService {
    private static final String UNREADABLE_JSON_WARNING =
            "The saved preset could not be read. Exploration was used as its recoverable layout.";
    private static final String EXPLORATION_KEY = "builtin:exploration";

    private final CockpitLayoutPresetRepository repository;
    private final CockpitLayoutCodec codec;
    private final CockpitBuiltInPresetCatalog builtIns;
    private final CockpitLayoutValidator validator;
    private final CockpitLayoutResolver resolver;

    public CockpitLayoutPresetService(
            CockpitLayoutPresetRepository repository,
            CockpitLayoutCodec codec,
            CockpitBuiltInPresetCatalog builtIns,
            CockpitLayoutValidator validator,
            CockpitLayoutResolver resolver) {
        this.repository = repository;
        this.codec = codec;
        this.builtIns = builtIns;
        this.validator = validator;
        this.resolver = resolver;
    }

    public record PresetDto(
            String key,
            UUID id,
            String name,
            boolean builtIn,
            long version,
            CockpitLayoutDocument layout,
            List<String> warnings) {
        public PresetDto {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record SavePresetRequest(String name, CockpitLayoutDocument layout) {
    }

    public record UpdatePresetRequest(String name, long version, CockpitLayoutDocument layout) {
    }

    @Transactional(readOnly = true)
    public List<PresetDto> list() {
        List<PresetDto> result = new ArrayList<>();
        for (CockpitBuiltInPresetCatalog.BuiltInPreset preset : builtIns.all()) {
            result.add(new PresetDto(
                    preset.key(),
                    null,
                    preset.name(),
                    true,
                    0,
                    preset.layout(),
                    List.of()));
        }
        for (CockpitLayoutPreset entity : repository.findAllByOrderByNormalizedNameAsc()) {
            result.add(toCustomDto(entity));
        }
        return result;
    }

    @Transactional
    public PresetDto create(SavePresetRequest request) {
        String trimmedName = requireValidName(request.name());
        requireMatchingLayoutName(trimmedName, request.layout());
        validator.validateForSave(request.layout());
        String normalized = normalize(trimmedName);
        if (repository.existsByNormalizedName(normalized)) {
            throw new IllegalArgumentException(
                    "A cockpit preset named '" + trimmedName + "' already exists.");
        }

        CockpitLayoutPreset entity = new CockpitLayoutPreset();
        entity.setName(trimmedName);
        entity.setNormalizedName(normalized);
        entity.setLayoutSchemaVersion(CockpitLayoutDocument.CURRENT_SCHEMA_VERSION);
        entity.setLayoutJson(codec.write(request.layout()));
        CockpitLayoutPreset saved = repository.saveAndFlush(entity);
        return toSavedDto(saved, request.layout());
    }

    @Transactional
    public PresetDto update(UUID id, UpdatePresetRequest request) {
        CockpitLayoutPreset entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cockpit preset not found"));
        if (entity.getVersion() != request.version()) {
            throw new OptimisticLockingFailureException("Cockpit preset changed");
        }

        String trimmedName = requireValidName(request.name());
        requireMatchingLayoutName(trimmedName, request.layout());
        validator.validateForSave(request.layout());
        String normalized = normalize(trimmedName);
        if (repository.existsByNormalizedNameAndIdNot(normalized, id)) {
            throw new IllegalArgumentException(
                    "A cockpit preset named '" + trimmedName + "' already exists.");
        }

        entity.setName(trimmedName);
        entity.setNormalizedName(normalized);
        entity.setLayoutSchemaVersion(CockpitLayoutDocument.CURRENT_SCHEMA_VERSION);
        entity.setLayoutJson(codec.write(request.layout()));
        CockpitLayoutPreset saved = repository.saveAndFlush(entity);
        return toSavedDto(saved, request.layout());
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Cockpit preset not found");
        }
        repository.deleteById(id);
    }

    private PresetDto toCustomDto(CockpitLayoutPreset entity) {
        List<String> warnings = new ArrayList<>();
        CockpitLayoutDocument layout;
        try {
            CockpitLayoutDocument decoded = codec.read(entity.getLayoutJson());
            CockpitLayoutResolver.Resolution resolution = resolver.resolve(decoded);
            layout = withName(resolution.document(), entity.getName());
            warnings.addAll(resolution.warnings());
        } catch (IllegalArgumentException unreadable) {
            CockpitLayoutDocument exploration = builtIns.require(EXPLORATION_KEY).layout();
            layout = withName(exploration, entity.getName());
            warnings.add(UNREADABLE_JSON_WARNING);
        }
        return new PresetDto(
                "custom:" + entity.getId(),
                entity.getId(),
                entity.getName(),
                false,
                entity.getVersion(),
                layout,
                warnings);
    }

    private PresetDto toSavedDto(CockpitLayoutPreset entity, CockpitLayoutDocument layout) {
        return new PresetDto(
                "custom:" + entity.getId(),
                entity.getId(),
                entity.getName(),
                false,
                entity.getVersion(),
                layout,
                List.of());
    }

    private static CockpitLayoutDocument withName(CockpitLayoutDocument source, String name) {
        return new CockpitLayoutDocument(
                source.schemaVersion(),
                name,
                source.zones(),
                source.ratios(),
                source.compactModuleKeys());
    }

    private static String requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Preset name must contain 1 to 80 characters");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            throw new IllegalArgumentException("Preset name must contain 1 to 80 characters");
        }
        return trimmed;
    }

    private static void requireMatchingLayoutName(String trimmedName, CockpitLayoutDocument layout) {
        if (layout == null || layout.name() == null
                || !layout.name().trim().equals(trimmedName)) {
            throw new IllegalArgumentException("Preset name and layout name must match.");
        }
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
