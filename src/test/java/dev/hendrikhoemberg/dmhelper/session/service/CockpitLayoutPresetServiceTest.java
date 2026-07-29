package dev.hendrikhoemberg.dmhelper.session.service;

import dev.hendrikhoemberg.dmhelper.session.data.CockpitLayoutPreset;
import dev.hendrikhoemberg.dmhelper.session.data.CockpitLayoutPresetRepository;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitBuiltInPresetCatalog;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitLayoutCodec;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitLayoutDocument;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitLayoutResolver;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitLayoutValidator;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitModuleRegistry;
import dev.hendrikhoemberg.dmhelper.session.layout.CockpitZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.hendrikhoemberg.dmhelper.session.service.CockpitLayoutPresetService.SavePresetRequest;
import static dev.hendrikhoemberg.dmhelper.session.service.CockpitLayoutPresetService.UpdatePresetRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CockpitLayoutPresetServiceTest {
    private static final UUID CUSTOM_ID =
            UUID.fromString("31ae49e1-0182-44aa-bdb5-127dc75197c9");
    @Mock CockpitLayoutPresetRepository repository;
    @Mock CockpitLayoutCodec codec;
    CockpitBuiltInPresetCatalog builtIns;
    CockpitLayoutPresetService service;
    CockpitLayoutResolver resolver;

    @BeforeEach
    void setUp() {
        CockpitModuleRegistry registry = CockpitModuleRegistry.standard();
        builtIns = new CockpitBuiltInPresetCatalog();
        resolver = new CockpitLayoutResolver(registry, builtIns);
        service = new CockpitLayoutPresetService(repository, codec, builtIns,
                new CockpitLayoutValidator(registry),
                resolver);
        lenient().when(codec.write(any())).thenReturn("{\"schemaVersion\":1}");
        lenient().when(codec.read(any())).thenReturn(explorationNamed("My Table"));
        lenient().when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            CockpitLayoutPreset entity = invocation.getArgument(0);
            if (entity.getId() == null) entity.setId(CUSTOM_ID);
            return entity;
        });
    }

    private CockpitLayoutPreset customEntity() {
        CockpitLayoutPreset entity = new CockpitLayoutPreset();
        entity.setId(CUSTOM_ID);
        entity.setName("My Table");
        entity.setNormalizedName("my table");
        entity.setLayoutSchemaVersion(1);
        entity.setLayoutJson("{\"schemaVersion\":1}");
        return entity;
    }

    private CockpitLayoutDocument explorationNamed(String name) {
        CockpitLayoutDocument source =
                builtIns.require("builtin:exploration").layout();
        return new CockpitLayoutDocument(1, name, source.zones(),
                source.ratios(), source.compactModuleKeys());
    }

    @Test
    void listPlacesImmutableBuiltInsBeforeNamedCustomPresets() {
        when(repository.findAllByOrderByNormalizedNameAsc()).thenReturn(List.of(customEntity()));
        assertThat(service.list()).extracting(CockpitLayoutPresetService.PresetDto::key)
                .containsExactly("builtin:exploration", "builtin:combat",
                        "builtin:theatre-of-mind",
                        "builtin:session-review", "custom:" + CUSTOM_ID);
    }

    @Test
    void createTrimsNameValidatesLayoutAndStoresNormalizedName() {
        CockpitLayoutPresetService.PresetDto created =
                service.create(new SavePresetRequest("  My Table  ", explorationNamed("My Table")));
        ArgumentCaptor<CockpitLayoutPreset> saved = ArgumentCaptor.forClass(CockpitLayoutPreset.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("My Table");
        assertThat(saved.getValue().getNormalizedName()).isEqualTo("my table");
        assertThat(created.builtIn()).isFalse();
    }

    @Test
    void duplicateNameAndMismatchedDocumentNameAreRejected() {
        when(repository.existsByNormalizedName("combat copy")).thenReturn(true);
        assertThatThrownBy(() -> service.create(
                new SavePresetRequest("Combat Copy", explorationNamed("Combat Copy"))))
                .hasMessage("A cockpit preset named 'Combat Copy' already exists.");
        assertThatThrownBy(() -> service.create(
                new SavePresetRequest("A", explorationNamed("B"))))
                .hasMessage("Preset name and layout name must match.");
    }

    @Test
    void unreadableStoredJsonFallsBackAndReturnsAWarning() {
        CockpitLayoutPreset broken = customEntity();
        broken.setLayoutJson("{broken");
        when(repository.findAllByOrderByNormalizedNameAsc()).thenReturn(List.of(broken));
        when(codec.read("{broken")).thenThrow(
                new IllegalArgumentException("Cockpit layout JSON could not be read."));
        assertThat(service.list().getLast()).satisfies(dto -> {
            assertThat(dto.name()).isEqualTo("My Table");
            assertThat(dto.layout().name()).isEqualTo("My Table");
            assertThat(dto.warnings()).contains(
                    "The saved preset could not be read. Exploration was used as its recoverable layout.");
        });
    }

    @Test
    void updateRejectsAStaleRequestVersionBeforeWriting() {
        CockpitLayoutPreset existing = customEntity();
        existing.setVersion(7);
        when(repository.findById(CUSTOM_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(CUSTOM_ID,
                new UpdatePresetRequest("My Table", 6, explorationNamed("My Table"))))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void unknownPresetFallsBackToExplorationInsteadOfFailing() {
        var resolved = resolver.resolve("builtin:presentation", CockpitZone.values());

        assertThat(resolved).isNotNull();
        assertThat(resolved.fallbackKey()).isEqualTo("builtin:exploration");
    }

    @Test
    void deleteTouchesOnlyTheApplicationLocalPresetRepository() {
        when(repository.existsById(CUSTOM_ID)).thenReturn(true);
        service.delete(CUSTOM_ID);
        verify(repository).existsById(CUSTOM_ID);
        verify(repository).deleteById(CUSTOM_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void combatPresetIncludesTheReferenceModule() {
        var preset = builtIns.require("builtin:combat");
        var moduleKeys = preset.layout().zones().values().stream()
                .flatMap(z -> z.moduleKeys().stream())
                .toList();
        assertThat(moduleKeys).contains("reference");
    }
}
