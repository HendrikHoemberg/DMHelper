# Import/Export Flow

## Import Pipeline Stages

The V2 campaign import (coordinated by `CampaignImportCoordinator`) proceeds through these stages:

1. **Container** — Read raw ZIP or JSON container via `CampaignPackageReader`; stage assets to temp directory.
2. **Schema** — `CampaignManifestV2SchemaValidator` validates the manifest JSON against the JSON Schema.
3. **Semantic** — `CampaignManifestV2SemanticValidator` checks referential integrity and business rules.
4. **Catalog** — Verify catalog references (SRD keys) against `CampaignCatalogService.snapshot()`.
5. **Spatial/Assets** — Validate asset signatures via `AssetSignatureValidator`; check tokens and primitives within map bounds.
6. **Migrate** — `LegacyV1ToV2Migration` handles format upgrade from V1 manifests; `FormatMigrationRegistry` runs schema-version migrations.
7. **Preview** — Results stored in `CampaignImportPreviewStore`; returned as `CampaignImportPreview` with problem list (ERROR/WARNING).
8. **Atomic Persist** — `confirm()` iterates registry importers in order, `entityManager.flush()`, then discards preview.

## Export Adapter Order

Export is driven by `CampaignSectionRegistry` which sorts `CampaignSectionExporter` implementations by `order()`. The `CampaignExportCoordinator` iterates `registry.exporters()` to assemble each section into the manifest.

The adapters implementing `CampaignSectionExporter` and `CampaignSectionImporter` include:

- `CampaignSectionAdapter` (order 100) — Campaign metadata and settings
- `SourceAnnotationSectionAdapter` — Source annotations from import provenance
- Additional section adapters registered via `CampaignSectionRegistry` sorted by order

Each adapter is a Spring `@Component` implementing the `CampaignSectionExporter` and/or `CampaignSectionImporter` interfaces from `dev.hendrikhoemberg.dmhelper.campaign.packagev2.section`.

## Key Classes

- `CampaignImportCoordinator` — Orchestrates the import pipeline (preview, confirm, revalidate assets)
- `CampaignExportCoordinator` — Orchestrates export (iterate adapters, assemble manifest, validate, produce artifact)
- `CampaignSectionAdapter` — Adapts core campaign entity (metadata, settings, calendar config, current scene ref)
- `CampaignSectionRegistry` — Discovers and sorts all section adapters by order
- `CampaignPackageKeyService` — Manages stable package keys for entities
- `CampaignManifestV2` — The manifest model (DTOs for each section)
- `CampaignManifestAssembler` — Builds the manifest from section exporters
- `CampaignAssetCollector` — Collects asset descriptors and byte data during export
- `CampaignCatalogService` — Provides catalog snapshot for version pinning
