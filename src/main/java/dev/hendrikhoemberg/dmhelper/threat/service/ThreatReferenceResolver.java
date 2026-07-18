package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReferenceRole;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ThreatReferenceResolver {

    private final ConditionRepository conditionRepository;
    private final StatBlockRepository statBlockRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final MagicItemRepository magicItemRepository;
    private final TrapRepository trapRepository;
    private final HazardRepository hazardRepository;

    public ThreatReferenceResolver(ConditionRepository conditionRepository,
                                   StatBlockRepository statBlockRepository,
                                   EquipmentItemRepository equipmentItemRepository,
                                   MagicItemRepository magicItemRepository,
                                   TrapRepository trapRepository,
                                   HazardRepository hazardRepository) {
        this.conditionRepository = conditionRepository;
        this.statBlockRepository = statBlockRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.magicItemRepository = magicItemRepository;
        this.trapRepository = trapRepository;
        this.hazardRepository = hazardRepository;
    }

    public boolean roleMatchesType(ThreatReferenceWrite ref) {
        if (ref == null || ref.role() == null || ref.targetType() == null) {
            return false;
        }
        return switch (ref.role()) {
            case CONDITION -> ref.targetType() == CampaignContentType.CONDITION;
            case SALVAGE_ITEM -> ref.targetType() == CampaignContentType.EQUIPMENT_ITEM
                    || ref.targetType() == CampaignContentType.MAGIC_ITEM;
        };
    }

    public ResolvedThreatTarget require(UUID campaignIdOrNull, ThreatReferenceWrite ref) {
        if (ref == null) {
            throw new IllegalArgumentException("UNRESOLVED_REFERENCE: reference is required");
        }
        if (!roleMatchesType(ref)) {
            throw new IllegalArgumentException(
                    "UNRESOLVED_REFERENCE: role " + ref.role() + " does not match type " + ref.targetType());
        }
        if (ref.targetId() == null) {
            throw new IllegalArgumentException("UNRESOLVED_REFERENCE: targetId is required");
        }
        Object entity = findByTypeAndId(ref.targetType(), ref.targetId());
        if (entity == null) {
            throw new IllegalArgumentException(
                    "UNRESOLVED_REFERENCE: " + ref.targetType() + " " + ref.targetId() + " not found");
        }
        if (!isVisible(entity, campaignIdOrNull)) {
            throw new IllegalArgumentException(
                    "UNRESOLVED_REFERENCE: " + ref.targetType() + " " + ref.targetId()
                            + " is not visible in the destination scope");
        }
        return toResolved(ref.targetType(), entity);
    }

    public ResolvedThreatTarget requireStatBlock(UUID campaignIdOrNull, UUID statBlockId) {
        if (statBlockId == null) {
            throw new IllegalArgumentException("UNRESOLVED_REFERENCE: statBlockId is required");
        }
        StatBlock sb = statBlockRepository.findById(statBlockId).orElse(null);
        if (sb == null) {
            throw new IllegalArgumentException(
                    "UNRESOLVED_REFERENCE: STATBLOCK " + statBlockId + " not found");
        }
        if (!isVisible(sb, campaignIdOrNull)) {
            throw new IllegalArgumentException(
                    "UNRESOLVED_REFERENCE: STATBLOCK " + statBlockId
                            + " is not visible in the destination scope");
        }
        return toResolved(CampaignContentType.STATBLOCK, sb);
    }

    public boolean isVisibleToScope(CampaignContentType type, UUID targetId, UUID campaignIdOrNull) {
        Object entity = findByTypeAndId(type, targetId);
        return entity != null && isVisible(entity, campaignIdOrNull);
    }

    /**
     * Ensures a trap or hazard is visible to the given campaign scope (SRD/global always;
     * campaign content only when campaign IDs match).
     */
    public Object requireVisible(ThreatKind kind, UUID id, UUID campaignIdOrNull) {
        if (kind == null || id == null) {
            throw new NotFoundException("Threat not found");
        }
        return switch (kind) {
            case TRAP -> {
                Trap trap = trapRepository.findById(id)
                        .orElseThrow(() -> new NotFoundException("Trap not found: " + id));
                if (!isVisible(trap, campaignIdOrNull)) {
                    throw new NotFoundException("Trap not visible: " + id);
                }
                yield trap;
            }
            case HAZARD -> {
                Hazard hazard = hazardRepository.findById(id)
                        .orElseThrow(() -> new NotFoundException("Hazard not found: " + id));
                if (!isVisible(hazard, campaignIdOrNull)) {
                    throw new NotFoundException("Hazard not visible: " + id);
                }
                yield hazard;
            }
        };
    }

    private Object findByTypeAndId(CampaignContentType type, UUID id) {
        if (id == null || type == null) {
            return null;
        }
        return switch (type) {
            case CONDITION -> conditionRepository.findById(id).orElse(null);
            case STATBLOCK -> statBlockRepository.findById(id).orElse(null);
            case EQUIPMENT_ITEM -> equipmentItemRepository.findById(id).orElse(null);
            case MAGIC_ITEM -> magicItemRepository.findById(id).orElse(null);
            default -> null;
        };
    }

    private boolean isVisible(Object entity, UUID campaignIdOrNull) {
        UUID entityCampaignId = extractCampaignId(entity);
        ContentSource source = extractSource(entity);
        if (source == ContentSource.SRD || entityCampaignId == null) {
            return true;
        }
        return campaignIdOrNull != null && entityCampaignId.equals(campaignIdOrNull);
    }

    private UUID extractCampaignId(Object entity) {
        return switch (entity) {
            case Condition c -> c.getCampaign() != null ? c.getCampaign().getId() : null;
            case StatBlock sb -> sb.getCampaign() != null ? sb.getCampaign().getId() : null;
            case EquipmentItem ei -> ei.getCampaign() != null ? ei.getCampaign().getId() : null;
            case MagicItem mi -> mi.getCampaign() != null ? mi.getCampaign().getId() : null;
            case Trap t -> t.getCampaign() != null ? t.getCampaign().getId() : null;
            case Hazard h -> h.getCampaign() != null ? h.getCampaign().getId() : null;
            default -> null;
        };
    }

    private ContentSource extractSource(Object entity) {
        return switch (entity) {
            case Condition c -> c.getSource();
            case StatBlock sb -> sb.getSource();
            case EquipmentItem ei -> ei.getSource();
            case MagicItem mi -> mi.getSource();
            case Trap t -> t.getSource();
            case Hazard h -> h.getSource();
            default -> ContentSource.CUSTOM;
        };
    }

    private ResolvedThreatTarget toResolved(CampaignContentType type, Object entity) {
        return switch (entity) {
            case Condition c -> new ResolvedThreatTarget(
                    type, c.getId(), c.getName(), c.getSource(),
                    c.getCampaign() != null ? c.getCampaign().getId() : null);
            case StatBlock sb -> new ResolvedThreatTarget(
                    type, sb.getId(), sb.getName(), sb.getSource(),
                    sb.getCampaign() != null ? sb.getCampaign().getId() : null);
            case EquipmentItem ei -> new ResolvedThreatTarget(
                    type, ei.getId(), ei.getName(), ei.getSource(),
                    ei.getCampaign() != null ? ei.getCampaign().getId() : null);
            case MagicItem mi -> new ResolvedThreatTarget(
                    type, mi.getId(), mi.getName(), mi.getSource(),
                    mi.getCampaign() != null ? mi.getCampaign().getId() : null);
            default -> throw new IllegalArgumentException("Unsupported resolved entity: " + entity);
        };
    }
}
