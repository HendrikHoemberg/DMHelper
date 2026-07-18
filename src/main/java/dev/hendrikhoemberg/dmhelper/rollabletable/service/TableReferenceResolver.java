package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TableReferenceResolver {

    private final StatBlockRepository statBlockRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final MagicItemRepository magicItemRepository;
    private final NoteRepository noteRepository;
    private final RollableTableRepository rollableTableRepository;
    private final EncounterRepository encounterRepository;
    private final HandoutRepository handoutRepository;
    private final CampaignCatalogService catalogService;

    public TableReferenceResolver(StatBlockRepository statBlockRepository,
                                   EquipmentItemRepository equipmentItemRepository,
                                   MagicItemRepository magicItemRepository,
                                   NoteRepository noteRepository,
                                   RollableTableRepository rollableTableRepository,
                                   EncounterRepository encounterRepository,
                                   HandoutRepository handoutRepository,
                                   CampaignCatalogService catalogService) {
        this.statBlockRepository = statBlockRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.magicItemRepository = magicItemRepository;
        this.noteRepository = noteRepository;
        this.rollableTableRepository = rollableTableRepository;
        this.encounterRepository = encounterRepository;
        this.handoutRepository = handoutRepository;
        this.catalogService = catalogService;
    }

    public ResolvedTableReference require(UUID campaignIdOrNull, RollableTableReferenceWrite ref) {
        if (ref.scope() == dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope.CATALOG) {
            return resolveCatalog(ref);
        }
        return resolveEntity(campaignIdOrNull, ref);
    }

    private ResolvedTableReference resolveCatalog(RollableTableReferenceWrite ref) {
        var resolved = catalogService.resolve(
                ref.targetType(), ref.catalogRuleset(), ref.catalogSourceKey());
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException(
                    "UNRESOLVED_REFERENCE: catalog " + ref.targetType() + " " + ref.catalogSourceKey());
        }
        return new ResolvedTableReference(ref.scope(), ref.targetType(),
                null, ref.catalogRuleset(), ref.catalogSourceKey(), ref.displayText());
    }

    private ResolvedTableReference resolveEntity(UUID campaignIdOrNull, RollableTableReferenceWrite ref) {
        Object entity = findByTypeAndId(ref.targetType(), ref.targetId());
        if (entity == null) {
            throw new IllegalArgumentException(
                    "UNRESOLVED_REFERENCE: " + ref.targetType() + " " + ref.targetId() + " not found");
        }

        UUID entityCampaignId = extractCampaignId(entity);
        ContentSource source = extractSource(entity);

        if (campaignIdOrNull != null) {
            boolean isSrd = source == ContentSource.SRD;
            boolean isGlobalCustom = source == ContentSource.CUSTOM && entityCampaignId == null;

            if (!isSrd && !isGlobalCustom) {
                if (entityCampaignId != null && !entityCampaignId.equals(campaignIdOrNull)) {
                    throw new IllegalArgumentException(
                            "UNRESOLVED_REFERENCE: " + ref.targetType() + " " + ref.targetId()
                                    + " belongs to a different campaign");
                }
            }
        }

        return new ResolvedTableReference(ref.scope(), ref.targetType(), ref.targetId(),
                ref.catalogRuleset(), ref.catalogSourceKey(), ref.displayText());
    }

    public boolean isVisibleToCampaign(UUID tableId, UUID campaignId) {
        return rollableTableRepository.findById(tableId)
                .map(table -> {
                    if (table.getSource() == ContentSource.SRD) return true;
                    if (table.getCampaign() == null) return true; // global custom
                    return table.getCampaign().getId().equals(campaignId);
                })
                .orElse(false);
    }

    private Object findByTypeAndId(CampaignContentType type, UUID id) {
        if (id == null) return null;
        return switch (type) {
            case STATBLOCK -> statBlockRepository.findById(id).orElse(null);
            case EQUIPMENT_ITEM -> equipmentItemRepository.findById(id).orElse(null);
            case MAGIC_ITEM -> magicItemRepository.findById(id).orElse(null);
            case NOTE -> noteRepository.findById(id).orElse(null);
            case ROLLABLE_TABLE -> rollableTableRepository.findById(id).orElse(null);
            case ENCOUNTER -> encounterRepository.findById(id).orElse(null);
            case HANDOUT -> handoutRepository.findById(id).orElse(null);
            default -> null;
        };
    }

    private UUID extractCampaignId(Object entity) {
        return switch (entity) {
            case StatBlock sb -> sb.getCampaign() != null ? sb.getCampaign().getId() : null;
            case EquipmentItem ei -> ei.getCampaign() != null ? ei.getCampaign().getId() : null;
            case MagicItem mi -> mi.getCampaign() != null ? mi.getCampaign().getId() : null;
            case Note n -> n.getCampaign() != null ? n.getCampaign().getId() : null;
            case RollableTable rt -> rt.getCampaign() != null ? rt.getCampaign().getId() : null;
            case Encounter e -> e.getCampaign() != null ? e.getCampaign().getId() : null;
            case Handout h -> h.getCampaign() != null ? h.getCampaign().getId() : null;
            default -> null;
        };
    }

    private ContentSource extractSource(Object entity) {
        return switch (entity) {
            case StatBlock sb -> sb.getSource();
            case EquipmentItem ei -> ei.getSource();
            case MagicItem mi -> mi.getSource();
            case RollableTable rt -> rt.getSource();
            default -> ContentSource.CUSTOM;
        };
    }

    RollableTableRepository getRollableTableRepository() { return rollableTableRepository; }
    CampaignCatalogService getCatalogService() { return catalogService; }
}
