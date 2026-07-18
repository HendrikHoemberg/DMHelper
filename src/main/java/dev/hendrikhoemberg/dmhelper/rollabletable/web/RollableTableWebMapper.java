package dev.hendrikhoemberg.dmhelper.rollabletable.web;

import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntry;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReference;

import java.util.UUID;

public final class RollableTableWebMapper {

    private RollableTableWebMapper() {}

    public static RollableTableResponse from(RollableTable table) {
        UUID campaignId = table.getCampaign() == null ? null : table.getCampaign().getId();
        return new RollableTableResponse(
                table.getId(),
                table.getSourceKey(),
                table.getSource(),
                campaignId,
                provenance(table.getProvenance()),
                table.getName(),
                table.getDescription(),
                table.getAddressMode(),
                table.getRollExpression(),
                table.getCategory(),
                table.getTags(),
                table.getCreatedAt(),
                table.getEntries().stream().map(RollableTableWebMapper::entry).toList());
    }

    private static RollableTableResponse.ProvenanceResponse provenance(ContentProvenance provenance) {
        if (provenance == null) {
            return null;
        }
        return new RollableTableResponse.ProvenanceResponse(
                provenance.getSourceTitle(),
                provenance.getEditionVersion(),
                provenance.getSourceLocator(),
                provenance.getLicenseClassification(),
                provenance.getImportedAt(),
                provenance.getConverterId(),
                provenance.getConverterVersion(),
                provenance.getSourceHash(),
                provenance.getExtractionConfidence());
    }

    private static RollableTableResponse.EntryResponse entry(RollableTableEntry entry) {
        return new RollableTableResponse.EntryResponse(
                entry.getId(),
                entry.getEntryKey(),
                entry.getRangeStart(),
                entry.getRangeEnd(),
                entry.getWeight(),
                entry.getResultText(),
                entry.getQuantityExpression(),
                entry.getSortOrder(),
                entry.getReferences().stream().map(RollableTableWebMapper::reference).toList());
    }

    private static RollableTableResponse.ReferenceResponse reference(RollableTableEntryReference reference) {
        return new RollableTableResponse.ReferenceResponse(
                reference.getId(),
                reference.getTargetScope(),
                reference.getTargetType(),
                reference.getTargetId(),
                reference.getCatalogRuleset(),
                reference.getCatalogSourceKey(),
                reference.getDisplayText(),
                reference.getSortOrder());
    }
}
