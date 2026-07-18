package dev.hendrikhoemberg.dmhelper.rollabletable.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ProvenanceDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.RollableTableDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.RollableTableEntryDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntry;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReference;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class RollableTableSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final RollableTableRepository tableRepository;
    private final RollableTableExportClosureService closureService;

    public RollableTableSectionAdapter(RollableTableRepository tableRepository,
                                        RollableTableExportClosureService closureService) {
        this.tableRepository = tableRepository;
        this.closureService = closureService;
    }

    @Override
    public String sectionName() {
        return "RollableTables";
    }

    @Override
    public int order() {
        return 150;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        var closure = closureService.forCampaign(context.campaignId());

        List<RollableTableDto> dtos = closure.tableIds().stream()
                .flatMap(id -> tableRepository.findWithEntriesById(id).stream())
                .map(table -> toDto(table, context))
                .toList();
        target.rollableTables(dtos);

        exportClosureLibraryEntities(closure.libraryReferenceIds(), context, target);
    }

    private void exportClosureLibraryEntities(Map<CampaignContentType, Set<UUID>> refIds,
                                               CampaignExportContext context,
                                               CampaignManifestAssembler target) {
        for (var entry : refIds.entrySet()) {
            switch (entry.getKey()) {
                case STATBLOCK -> target.setClosureStatblockIds(entry.getValue());
                case MAGIC_ITEM -> target.setClosureMagicItemIds(entry.getValue());
                case EQUIPMENT_ITEM -> target.setClosureEquipmentIds(entry.getValue());
                case SPELL -> target.setClosureSpellIds(entry.getValue());
                default -> {
                }
            }
        }
    }

    private RollableTableDto toDto(RollableTable table, CampaignExportContext context) {
        String key = context.key(CampaignContentType.ROLLABLE_TABLE, table.getId(), table.getName());

        List<RollableTableEntryDto> entryDtos = table.getEntries().stream()
                .map(e -> toEntryDto(e, context))
                .toList();

        List<String> tags = parseTags(table.getTags());

        return new RollableTableDto(
                key,
                table.getSourceKey(),
                table.getName(),
                table.getDescription(),
                table.getAddressMode().name(),
                table.getRollExpression(),
                table.getCategory().name(),
                tags,
                entryDtos,
                toProvenanceDto(table.getProvenance())
        );
    }

    private RollableTableEntryDto toEntryDto(RollableTableEntry entry, CampaignExportContext context) {
        String entryKey = entry.getEntryKey();

        List<ContentReference> refs = entry.getReferences().stream()
                .map(ref -> toContentRef(ref, context))
                .filter(r -> r != null)
                .toList();

        return new RollableTableEntryDto(
                entryKey,
                entry.getRangeStart(),
                entry.getRangeEnd(),
                entry.getWeight(),
                entry.getResultText(),
                entry.getQuantityExpression(),
                refs
        );
    }

    private ContentReference toContentRef(RollableTableEntryReference ref, CampaignExportContext context) {
        CampaignContentType type;
        try {
            type = CampaignContentType.valueOf(ref.getTargetType());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (ref.getTargetScope() == TableReferenceScope.CATALOG) {
            return ContentReference.catalogRef(type, ref.getCatalogRuleset(), ref.getCatalogSourceKey());
        }
        if (ref.getTargetId() != null) {
            return context.packageRef(type, ref.getTargetId(), ref.getDisplayText());
        }
        return null;
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<RollableTableDto> dtos = source.rollableTables();
        if (dtos == null) return;

        var campaign = context.campaign();

        for (RollableTableDto dto : dtos) {
            RollableTable entity = new RollableTable();
            entity.setSource(ContentSource.CUSTOM);
            entity.setCampaign(campaign);
            entity.setSourceKey(dto.sourceKey());
            entity.setName(dto.name());
            entity.setDescription(dto.description());
            if (dto.addressMode() != null) {
                entity.setAddressMode(TableAddressMode.valueOf(dto.addressMode()));
            }
            entity.setRollExpression(dto.rollExpression());
            if (dto.category() != null) {
                entity.setCategory(TableCategory.valueOf(dto.category()));
            }
            entity.setTags(joinTags(dto.tags()));
            entity.setProvenance(toContentProvenance(dto.provenance()));

            if (dto.entries() != null) {
                for (int i = 0; i < dto.entries().size(); i++) {
                    RollableTableEntryDto entryDto = dto.entries().get(i);
                    RollableTableEntry entry = new RollableTableEntry();
                    entry.setTable(entity);
                    entry.setEntryKey(entryDto.key());
                    entry.setRangeStart(entryDto.rangeStart());
                    entry.setRangeEnd(entryDto.rangeEnd());
                    entry.setWeight(entryDto.weight());
                    entry.setResultText(entryDto.resultText());
                    entry.setQuantityExpression(entryDto.quantityExpression());
                    entry.setSortOrder(i);

                    if (entryDto.references() != null) {
                        for (int ri = 0; ri < entryDto.references().size(); ri++) {
                            ContentReference ref = entryDto.references().get(ri);
                            if (ref == null) continue;
                            RollableTableEntryReference entryRef = new RollableTableEntryReference();
                            entryRef.setEntry(entry);
                            entryRef.setSortOrder(ri);
                            if (ref.scope() == ContentReference.Scope.CATALOG) {
                                entryRef.setTargetScope(TableReferenceScope.CATALOG);
                                entryRef.setTargetType(ref.type().name());
                                entryRef.setCatalogRuleset(ref.ruleset());
                                entryRef.setCatalogSourceKey(ref.sourceKey());
                                entryRef.setDisplayText(ref.sourceKey());
                            } else {
                                entryRef.setTargetScope(TableReferenceScope.ENTITY);
                                entryRef.setTargetType(ref.type().name());
                                entryRef.setDisplayText(ref.key());
                            }
                            entry.getReferences().add(entryRef);
                        }
                    }
                    entity.getEntries().add(entry);
                }
            }

            tableRepository.save(entity);
            context.register(CampaignContentType.ROLLABLE_TABLE, dto.key(), entity, entity.getId());

            if (dto.entries() != null) {
                String tableKey = dto.key();
                context.defer("rollable-table-refs:" + tableKey, () -> {
                    RollableTable savedTable = context.require(
                            ContentReference.packageRef(CampaignContentType.ROLLABLE_TABLE, tableKey),
                            CampaignContentType.ROLLABLE_TABLE, RollableTable.class);
                    resolveEntryReferences(dto, savedTable, context);
                    tableRepository.save(savedTable);
                });
            }
        }
    }

    private void resolveEntryReferences(RollableTableDto dto, RollableTable table,
                                         CampaignImportContext context) {
        if (dto.entries() == null || table.getEntries() == null) return;
        for (int ei = 0; ei < dto.entries().size() && ei < table.getEntries().size(); ei++) {
            RollableTableEntryDto entryDto = dto.entries().get(ei);
            RollableTableEntry entry = table.getEntries().get(ei);
            if (entryDto.references() == null) continue;
            int refCount = Math.min(entryDto.references().size(), entry.getReferences().size());
            for (int ri = 0; ri < refCount; ri++) {
                ContentReference ref = entryDto.references().get(ri);
                RollableTableEntryReference entryRef = entry.getReferences().get(ri);
                if (ref == null) continue;
                if (ref.scope() == ContentReference.Scope.PACKAGE) {
                    try {
                        Object target = context.require(ref, ref.type(), Object.class);
                        if (target != null) {
                            UUID targetId = entityId(target);
                            entryRef.setTargetId(targetId);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        var result = new ArrayList<String>();
        for (var part : tags.split(",")) {
            var trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return String.join(",", tags);
    }

    private static ProvenanceDto toProvenanceDto(ContentProvenance p) {
        if (p == null) return null;
        return new ProvenanceDto(
                p.getSourceTitle(), p.getEditionVersion(), p.getSourceLocator(),
                p.getLicenseClassification(), p.getImportedAt(), p.getConverterId(),
                p.getConverterVersion(), p.getSourceHash(), p.getExtractionConfidence()
        );
    }

    private static ContentProvenance toContentProvenance(ProvenanceDto dto) {
        if (dto == null) return null;
        var p = new ContentProvenance();
        p.setSourceTitle(dto.sourceTitle());
        p.setEditionVersion(dto.editionVersion());
        p.setSourceLocator(dto.sourceLocator());
        p.setLicenseClassification(dto.licenseClassification());
        p.setImportedAt(dto.importedAt() != null ? dto.importedAt() : Instant.now());
        p.setConverterId(dto.converterId());
        p.setConverterVersion(dto.converterVersion());
        p.setSourceHash(dto.sourceHash());
        p.setExtractionConfidence(dto.extractionConfidence());
        return p;
    }

    private static UUID entityId(Object entity) {
        try {
            return (UUID) entity.getClass().getMethod("getId").invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot resolve package entity id for " + entity.getClass().getName(), e);
        }
    }
}
