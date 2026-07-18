package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.LibraryReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntry;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReference;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RollableTableService {

    public static final String DEP_KIND_TABLE_ENTRY = "TABLE_ENTRY";
    public static final String DEP_KIND_SCENE = "SCENE";
    public static final String DEP_KIND_LOCATION = "LOCATION";

    private final RollableTableRepository repository;
    private final CampaignRepository campaignRepository;
    private final CustomContentSupport customContentSupport;
    private final RollableTableValidator validator;
    private final TableReferenceResolver referenceResolver;
    private final RollableTableDependencyService dependencyService;
    private final LibraryReferenceCleaner libraryReferenceCleaner;
    private final SceneLinkRepository sceneLinkRepository;

    public RollableTableService(RollableTableRepository repository,
                                 CampaignRepository campaignRepository,
                                 CustomContentSupport customContentSupport,
                                 RollableTableValidator validator,
                                 TableReferenceResolver referenceResolver,
                                 RollableTableDependencyService dependencyService,
                                 LibraryReferenceCleaner libraryReferenceCleaner,
                                 SceneLinkRepository sceneLinkRepository) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.customContentSupport = customContentSupport;
        this.validator = validator;
        this.referenceResolver = referenceResolver;
        this.dependencyService = dependencyService;
        this.libraryReferenceCleaner = libraryReferenceCleaner;
        this.sceneLinkRepository = sceneLinkRepository;
    }

    @Transactional
    public RollableTable create(UUID campaignIdOrNull, RollableTableWrite write, ContentProvenance provenance) {
        validator.validate(write, null);

        RollableTable table = new RollableTable();
        table.setSource(ContentSource.CUSTOM);
        if (campaignIdOrNull != null) {
            campaignRepository.findById(campaignIdOrNull).ifPresent(table::setCampaign);
        }
        applyWrite(table, write, provenance);
        return repository.save(table);
    }

    @Transactional
    public RollableTable updateCustom(UUID id, RollableTableWrite write, ContentProvenance provenance) {
        RollableTable table = findById(id);
        customContentSupport.assertCustom(table.getSource());
        validator.validate(write, id);

        applyWrite(table, write, provenance);
        return repository.save(table);
    }

    @Transactional
    public RollableTable cloneAsCustom(UUID sourceId, UUID campaignIdOrNull, String newName) {
        RollableTable original = findById(sourceId);
        RollableTable clone = new RollableTable();
        clone.setSource(ContentSource.CUSTOM);
        if (campaignIdOrNull != null) {
            campaignRepository.findById(campaignIdOrNull).ifPresent(clone::setCampaign);
        }
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setSourceKey(customContentSupport.slugify(clone.getName()));
        clone.setDescription(original.getDescription());
        clone.setAddressMode(original.getAddressMode());
        clone.setRollExpression(original.getRollExpression());
        clone.setCategory(original.getCategory());
        clone.setTags(original.getTags());
        if (original.getProvenance() != null) {
            clone.setProvenance(original.getProvenance());
        } else {
            clone.setProvenance(customContentSupport.defaultForSrdClone());
        }

        int sortOrder = 0;
        for (var srcEntry : original.getEntries()) {
            RollableTableEntry entry = new RollableTableEntry();
            entry.setTable(clone);
            entry.setEntryKey(srcEntry.getEntryKey());
            entry.setRangeStart(srcEntry.getRangeStart());
            entry.setRangeEnd(srcEntry.getRangeEnd());
            entry.setWeight(srcEntry.getWeight());
            entry.setResultText(srcEntry.getResultText());
            entry.setQuantityExpression(srcEntry.getQuantityExpression());
            entry.setSortOrder(sortOrder++);
            clone.getEntries().add(entry);

            int refSortOrder = 0;
            for (var srcRef : srcEntry.getReferences()) {
                RollableTableEntryReference ref = new RollableTableEntryReference();
                ref.setEntry(entry);
                ref.setTargetScope(srcRef.getTargetScope());
                ref.setTargetType(srcRef.getTargetType());
                ref.setTargetId(srcRef.getTargetId());
                ref.setCatalogRuleset(srcRef.getCatalogRuleset());
                ref.setCatalogSourceKey(srcRef.getCatalogSourceKey());
                ref.setDisplayText(srcRef.getDisplayText());
                ref.setSortOrder(refSortOrder++);
                entry.getReferences().add(ref);
            }
        }

        return repository.save(clone);
    }

    @Transactional
    public RollableTable promoteToGlobal(UUID id) {
        RollableTable table = findById(id);
        customContentSupport.assertCustom(table.getSource());

        for (var entry : table.getEntries()) {
            for (var ref : entry.getReferences()) {
                if (ref.getTargetScope() == TableReferenceScope.ENTITY) {
                    if (!referenceResolver.isVisibleToCampaign(ref.getTargetId(), null)) {
                        throw new IllegalArgumentException(
                                "Cannot promote: reference to " + ref.getTargetType() + " " + ref.getTargetId()
                                        + " would cross scope");
                    }
                }
            }
        }

        if (table.getCampaign() != null) {
            UUID oldCampaignId = table.getCampaign().getId();
            table.setCampaign(null);
            libraryReferenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.ROLLABLE_TABLE, id);
        }
        return repository.save(table);
    }

    @Transactional(readOnly = true)
    public TableDeletionImpact deletionImpact(UUID id) {
        return dependencyService.computeDeletionImpact(id);
    }

    @Transactional
    public void deleteCustom(UUID id, boolean confirmed) {
        RollableTable table = findById(id);
        customContentSupport.assertCustom(table.getSource());

        TableDeletionImpact impact = dependencyService.computeDeletionImpact(id);

        if (!confirmed && impact.hasDependents()) {
            throw new IllegalArgumentException(
                    "Table has " + impact.dependencies().size() + " dependent(s); set confirmed=true to proceed");
        }

        if (confirmed) {
            for (var link : sceneLinkRepository.findByTargetId(id)) {
                var scene = link.getScene();
                scene.getLinks().remove(link);
                sceneLinkRepository.delete(link);
            }

            for (var dep : impact.dependencies()) {
                if (DEP_KIND_TABLE_ENTRY.equals(dep.kind())) {
                    var referencingTable = repository.findById(dep.dependentId());
                    referencingTable.ifPresent(rt -> {
                        for (var entry : rt.getEntries()) {
                            var refsToRemove = entry.getReferences().stream()
                                    .filter(r -> r.getTargetId() != null && r.getTargetId().equals(id)
                                            && "ROLLABLE_TABLE".equals(r.getTargetType()))
                                    .toList();
                            for (var ref : refsToRemove) {
                                String marker = "[Deleted table reference: " + table.getName() + "]";
                                String existing = entry.getResultText();
                                if (existing != null && !existing.isBlank()) {
                                    entry.setResultText(existing + " " + marker);
                                } else {
                                    entry.setResultText(marker);
                                }
                                entry.getReferences().remove(ref);
                            }
                        }
                    });
                }
            }
        }

        if (table.getCampaign() != null) {
            libraryReferenceCleaner.deletePackageKey(table.getCampaign().getId(),
                    CampaignContentType.ROLLABLE_TABLE, id);
        }

        repository.delete(table);
    }

    private RollableTable findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("RollableTable not found: " + id));
    }

    private void applyWrite(RollableTable table, RollableTableWrite write, ContentProvenance provenance) {
        table.setName(write.name());
        if (write.sourceKey() != null && !write.sourceKey().isBlank()) {
            table.setSourceKey(write.sourceKey());
        } else if (table.getSourceKey() == null) {
            table.setSourceKey(customContentSupport.slugify(write.name()));
        }
        if (write.description() != null) table.setDescription(write.description());
        table.setAddressMode(write.addressMode());
        if (write.addressMode() == TableAddressMode.WEIGHTED && write.rollExpression() == null) {
            int totalWeight = 0;
            if (write.entries() != null) {
                for (var e : write.entries()) {
                    if (e.weight() != null) {
                        totalWeight = Math.addExact(totalWeight, e.weight());
                    }
                }
            }
            table.setRollExpression("1d" + totalWeight);
        } else {
            table.setRollExpression(write.rollExpression());
        }
        table.setCategory(write.category());
        if (write.tags() != null) {
            if (write.tags().isEmpty()) {
                table.setTags(null);
            } else {
                table.setTags(String.join(", ", write.tags()));
            }
        }
        if (provenance != null) {
            table.setProvenance(provenance);
        } else if (table.getProvenance() == null) {
            table.setProvenance(customContentSupport.defaultForCreate(null));
        }

        table.getEntries().clear();
        if (write.entries() != null) {
            int sortOrder = 0;
            for (var entryWrite : write.entries()) {
                RollableTableEntry entry = new RollableTableEntry();
                entry.setTable(table);
                entry.setEntryKey(entryWrite.key() != null ? entryWrite.key()
                        : "entry-" + sortOrder);
                entry.setRangeStart(entryWrite.rangeStart());
                entry.setRangeEnd(entryWrite.rangeEnd());
                entry.setWeight(entryWrite.weight());
                entry.setResultText(entryWrite.resultText());
                entry.setQuantityExpression(entryWrite.quantityExpression());
                entry.setSortOrder(sortOrder++);
                table.getEntries().add(entry);

                if (entryWrite.references() != null) {
                    int refSortOrder = 0;
                    for (var refWrite : entryWrite.references()) {
                        RollableTableEntryReference ref = new RollableTableEntryReference();
                        ref.setEntry(entry);
                        ref.setTargetScope(refWrite.scope());
                        ref.setTargetType(refWrite.targetType().name());
                        ref.setTargetId(refWrite.targetId());
                        ref.setCatalogRuleset(refWrite.catalogRuleset());
                        ref.setCatalogSourceKey(refWrite.catalogSourceKey());
                        ref.setDisplayText(refWrite.displayText());
                        ref.setSortOrder(refSortOrder++);
                        entry.getReferences().add(ref);
                    }
                }
            }
        }
    }
}
