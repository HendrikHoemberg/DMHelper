package dev.hendrikhoemberg.dmhelper.threat.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.HazardDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ProvenanceDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ThreatCheckDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ThreatDamageDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.TrapDisarmMethodDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.TrapDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.packagev2.StatBlockReferenceResolver;
import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheck;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheckMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReference;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReferenceRole;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapDisarmMethod;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class ThreatSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final TrapRepository trapRepository;
    private final HazardRepository hazardRepository;
    private final ThreatExportClosureService closureService;
    private final StatBlockReferenceResolver statBlockResolver;
    private final ConditionRepository conditionRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final MagicItemRepository magicItemRepository;

    public ThreatSectionAdapter(TrapRepository trapRepository,
                                HazardRepository hazardRepository,
                                ThreatExportClosureService closureService,
                                StatBlockReferenceResolver statBlockResolver,
                                ConditionRepository conditionRepository,
                                EquipmentItemRepository equipmentItemRepository,
                                MagicItemRepository magicItemRepository) {
        this.trapRepository = trapRepository;
        this.hazardRepository = hazardRepository;
        this.closureService = closureService;
        this.statBlockResolver = statBlockResolver;
        this.conditionRepository = conditionRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.magicItemRepository = magicItemRepository;
    }

    @Override
    public String sectionName() {
        return "Threats";
    }

    @Override
    public int order() {
        return 250;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        var closure = closureService.forCampaign(context.campaignId());

        List<TrapDto> traps = closure.trapIds().stream()
                .flatMap(id -> trapRepository.findDetailedById(id).stream())
                .map(trap -> toTrapDto(trap, context))
                .toList();
        List<HazardDto> hazards = closure.hazardIds().stream()
                .flatMap(id -> hazardRepository.findDetailedById(id).stream())
                .map(hazard -> toHazardDto(hazard, context))
                .toList();
        target.traps(traps);
        target.hazards(hazards);
        exportClosureLibraryEntities(closure.libraryReferenceIds(), target);
    }

    private void exportClosureLibraryEntities(
            java.util.Map<CampaignContentType, Set<UUID>> refIds,
            CampaignManifestAssembler target) {
        for (var entry : refIds.entrySet()) {
            switch (entry.getKey()) {
                case STATBLOCK -> target.setClosureStatblockIds(entry.getValue());
                case MAGIC_ITEM -> target.setClosureMagicItemIds(entry.getValue());
                case EQUIPMENT_ITEM -> target.setClosureEquipmentIds(entry.getValue());
                case CONDITION -> target.setClosureConditionIds(entry.getValue());
                default -> { }
            }
        }
    }

    private TrapDto toTrapDto(Trap trap, CampaignExportContext context) {
        String key = context.key(CampaignContentType.TRAP, trap.getId(), trap.getName());
        List<TrapDisarmMethodDto> disarmMethods = trap.getDisarmMethods().stream()
                .map(m -> new TrapDisarmMethodDto(
                        m.getMethodKey(), m.getLabel(), m.getAbility(), m.getSkill(), m.getTool(),
                        m.getDc(), m.getFailureConsequence(), m.getSortOrder()))
                .toList();
        ContentReference statBlockRef = trap.getStatBlock() != null
                ? statBlockResolver.referenceFor(trap.getStatBlock(), context)
                : null;
        List<ContentReference> conditionRefs = new ArrayList<>();
        List<ContentReference> salvageItemRefs = new ArrayList<>();
        for (ThreatReference ref : trap.getReferences()) {
            ContentReference contentRef = toContentRef(ref, context);
            if (contentRef == null) continue;
            if (ref.getRole() == ThreatReferenceRole.CONDITION) {
                conditionRefs.add(contentRef);
            } else if (ref.getRole() == ThreatReferenceRole.SALVAGE_ITEM) {
                salvageItemRefs.add(contentRef);
            }
        }
        return new TrapDto(
                key,
                trap.getSourceKey(),
                trap.getName(),
                trap.getDescription(),
                trap.getSeverity() != null ? trap.getSeverity().name() : null,
                trap.getMinLevel(),
                trap.getMaxLevel(),
                trap.getTriggerDescription(),
                trap.getTriggerAreaHint(),
                trap.getDetectionPassiveThreshold(),
                toCheckDto(trap.getDetectionCheck()),
                disarmMethods,
                trap.getAttackBonus(),
                toCheckDto(trap.getSave()),
                toDamageDto(trap.getDamageExpression(), trap.getDamageTypes()),
                trap.getAdditionalEffect(),
                trap.getResetMode() != null ? trap.getResetMode().name() : null,
                trap.getResetTiming(),
                statBlockRef,
                trap.getCountermeasureNotes(),
                conditionRefs,
                salvageItemRefs,
                trap.getCreatedAt(),
                toProvenanceDto(trap.getProvenance())
        );
    }

    private HazardDto toHazardDto(Hazard hazard, CampaignExportContext context) {
        String key = context.key(CampaignContentType.HAZARD, hazard.getId(), hazard.getName());
        List<ContentReference> conditionRefs = new ArrayList<>();
        List<ContentReference> salvageItemRefs = new ArrayList<>();
        for (ThreatReference ref : hazard.getReferences()) {
            ContentReference contentRef = toContentRef(ref, context);
            if (contentRef == null) continue;
            if (ref.getRole() == ThreatReferenceRole.CONDITION) {
                conditionRefs.add(contentRef);
            } else if (ref.getRole() == ThreatReferenceRole.SALVAGE_ITEM) {
                salvageItemRefs.add(contentRef);
            }
        }
        return new HazardDto(
                key,
                hazard.getSourceKey(),
                hazard.getName(),
                hazard.getDescription(),
                hazard.getSeverity() != null ? hazard.getSeverity().name() : null,
                hazard.getMinLevel(),
                hazard.getMaxLevel(),
                hazard.getExposureMode() != null ? hazard.getExposureMode().name() : null,
                hazard.getExposureText(),
                hazard.getAreaHint(),
                toCheckDto(hazard.getCheck()),
                toDamageDto(hazard.getDamageExpression(), hazard.getDamageTypes()),
                hazard.getEscalationText(),
                hazard.getEndingConditions(),
                conditionRefs,
                salvageItemRefs,
                hazard.getCreatedAt(),
                toProvenanceDto(hazard.getProvenance())
        );
    }

    private ContentReference toContentRef(ThreatReference ref, CampaignExportContext context) {
        if (ref.getTargetType() == null || ref.getTargetId() == null) return null;
        return switch (ref.getTargetType()) {
            case CONDITION -> conditionRef(ref.getTargetId(), context);
            case EQUIPMENT_ITEM -> equipmentRef(ref.getTargetId(), context);
            case MAGIC_ITEM -> magicItemRef(ref.getTargetId(), context);
            case STATBLOCK -> context.packageRef(CampaignContentType.STATBLOCK, ref.getTargetId(),
                    ref.getDisplayText() != null ? ref.getDisplayText() : "statblock");
            default -> null;
        };
    }

    private ContentReference conditionRef(UUID id, CampaignExportContext context) {
        return conditionRepository.findById(id).map(c -> {
            if (c.getSource() == ContentSource.SRD) {
                return context.catalogRef(CampaignContentType.CONDITION, c.getSourceKey());
            }
            return context.packageRef(CampaignContentType.CONDITION, c.getId(), c.getName());
        }).orElse(null);
    }

    private ContentReference equipmentRef(UUID id, CampaignExportContext context) {
        return equipmentItemRepository.findById(id).map(e -> {
            if (e.getSource() == ContentSource.SRD) {
                return context.catalogRef(CampaignContentType.EQUIPMENT_ITEM, e.getSourceKey());
            }
            return context.packageRef(CampaignContentType.EQUIPMENT_ITEM, e.getId(), e.getName());
        }).orElse(null);
    }

    private ContentReference magicItemRef(UUID id, CampaignExportContext context) {
        return magicItemRepository.findById(id).map(m -> {
            if (m.getSource() == ContentSource.SRD) {
                return context.catalogRef(CampaignContentType.MAGIC_ITEM, m.getSourceKey());
            }
            return context.packageRef(CampaignContentType.MAGIC_ITEM, m.getId(), m.getName());
        }).orElse(null);
    }

    private static ThreatCheckDto toCheckDto(ThreatCheck check) {
        if (check == null) return null;
        if (check.getMode() == null && isBlank(check.getAbility()) && isBlank(check.getSkill())
                && check.getDc() == null) {
            return null;
        }
        return new ThreatCheckDto(
                check.getMode() != null ? check.getMode().name() : null,
                check.getAbility(), check.getSkill(), check.getDc());
    }

    private static ThreatDamageDto toDamageDto(String expression, List<DamageType> types) {
        if (isBlank(expression) && (types == null || types.isEmpty())) return null;
        List<String> typeNames = types == null ? List.of()
                : types.stream().map(Enum::name).toList();
        return new ThreatDamageDto(expression, typeNames);
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<TrapDto> trapDtos = source.traps();
        if (trapDtos != null) {
            for (TrapDto dto : trapDtos) {
                Trap trap = new Trap();
                trap.setSource(ContentSource.CUSTOM);
                trap.setCampaign(context.campaign());
                applyTrapDto(trap, dto);
                trapRepository.save(trap);
                context.register(CampaignContentType.TRAP, dto.key(), trap, trap.getId());
                context.defer("trap-refs:" + dto.key(), () -> resolveTrapRefs(dto, trap, context));
            }
        }

        List<HazardDto> hazardDtos = source.hazards();
        if (hazardDtos != null) {
            for (HazardDto dto : hazardDtos) {
                Hazard hazard = new Hazard();
                hazard.setSource(ContentSource.CUSTOM);
                hazard.setCampaign(context.campaign());
                applyHazardDto(hazard, dto);
                hazardRepository.save(hazard);
                context.register(CampaignContentType.HAZARD, dto.key(), hazard, hazard.getId());
                context.defer("hazard-refs:" + dto.key(), () -> resolveHazardRefs(dto, hazard, context));
            }
        }
    }

    private void applyTrapDto(Trap trap, TrapDto dto) {
        trap.setSourceKey(dto.sourceKey() != null ? dto.sourceKey() : dto.key());
        trap.setName(dto.name());
        trap.setDescription(dto.description());
        if (dto.severity() != null) {
            trap.setSeverity(ThreatSeverity.valueOf(dto.severity()));
        }
        trap.setMinLevel(dto.minLevel());
        trap.setMaxLevel(dto.maxLevel());
        trap.setTriggerDescription(dto.triggerDescription());
        trap.setTriggerAreaHint(dto.triggerAreaHint());
        trap.setDetectionPassiveThreshold(dto.detectionPassiveThreshold());
        trap.setDetectionCheck(toThreatCheck(dto.detectionCheck()));
        trap.setAttackBonus(dto.attackBonus());
        trap.setSave(toThreatCheck(dto.save()));
        if (dto.damage() != null) {
            trap.setDamageExpression(dto.damage().expression());
            trap.getDamageTypes().clear();
            if (dto.damage().types() != null) {
                for (String type : dto.damage().types()) {
                    trap.getDamageTypes().add(DamageType.valueOf(type));
                }
            }
        }
        trap.setAdditionalEffect(dto.additionalEffect());
        trap.setResetMode(dto.resetMode() != null
                ? ThreatResetMode.valueOf(dto.resetMode()) : ThreatResetMode.NONE);
        trap.setResetTiming(dto.resetTiming());
        trap.setCountermeasureNotes(dto.countermeasureNotes());
        trap.setProvenance(toContentProvenance(dto.provenance()));
        if (dto.createdAt() != null) {
            trap.setCreatedAt(dto.createdAt());
        }
        trap.getDisarmMethods().clear();
        if (dto.disarmMethods() != null) {
            for (TrapDisarmMethodDto methodDto : dto.disarmMethods()) {
                TrapDisarmMethod method = new TrapDisarmMethod();
                method.setTrap(trap);
                method.setMethodKey(methodDto.key());
                method.setLabel(methodDto.label());
                method.setAbility(methodDto.ability());
                method.setSkill(methodDto.skill());
                method.setTool(methodDto.tool());
                method.setDc(methodDto.dc());
                method.setFailureConsequence(methodDto.failureConsequence());
                method.setSortOrder(methodDto.sortOrder());
                trap.getDisarmMethods().add(method);
            }
        }
    }

    private void applyHazardDto(Hazard hazard, HazardDto dto) {
        hazard.setSourceKey(dto.sourceKey() != null ? dto.sourceKey() : dto.key());
        hazard.setName(dto.name());
        hazard.setDescription(dto.description());
        if (dto.severity() != null) {
            hazard.setSeverity(ThreatSeverity.valueOf(dto.severity()));
        }
        hazard.setMinLevel(dto.minLevel());
        hazard.setMaxLevel(dto.maxLevel());
        if (dto.exposureMode() != null) {
            hazard.setExposureMode(HazardExposureMode.valueOf(dto.exposureMode()));
        }
        hazard.setExposureText(dto.exposureText());
        hazard.setAreaHint(dto.areaHint());
        hazard.setCheck(toThreatCheck(dto.check()));
        if (dto.damage() != null) {
            hazard.setDamageExpression(dto.damage().expression());
            hazard.getDamageTypes().clear();
            if (dto.damage().types() != null) {
                for (String type : dto.damage().types()) {
                    hazard.getDamageTypes().add(DamageType.valueOf(type));
                }
            }
        }
        hazard.setEscalationText(dto.escalationText());
        hazard.setEndingConditions(dto.endingConditions());
        hazard.setProvenance(toContentProvenance(dto.provenance()));
        if (dto.createdAt() != null) {
            hazard.setCreatedAt(dto.createdAt());
        }
    }

    private void resolveTrapRefs(TrapDto dto, Trap trap, CampaignImportContext context) {
        Trap saved = trapRepository.findDetailedById(trap.getId()).orElse(trap);
        if (dto.statBlockRef() != null) {
            saved.setStatBlock(statBlockResolver.resolve(dto.statBlockRef(), context));
        }
        saved.getReferences().clear();
        int order = 0;
        if (dto.conditionRefs() != null) {
            for (ContentReference ref : dto.conditionRefs()) {
                if (ref == null) continue;
                ThreatReference entityRef = new ThreatReference();
                entityRef.setTrap(saved);
                entityRef.setRole(ThreatReferenceRole.CONDITION);
                entityRef.setTargetType(CampaignContentType.CONDITION);
                entityRef.setTargetId(resolveTargetId(ref, CampaignContentType.CONDITION, context));
                entityRef.setDisplayText(displayOf(ref));
                entityRef.setSortOrder(order++);
                saved.getReferences().add(entityRef);
            }
        }
        if (dto.salvageItemRefs() != null) {
            for (ContentReference ref : dto.salvageItemRefs()) {
                if (ref == null) continue;
                ThreatReference entityRef = new ThreatReference();
                entityRef.setTrap(saved);
                entityRef.setRole(ThreatReferenceRole.SALVAGE_ITEM);
                entityRef.setTargetType(ref.type());
                entityRef.setTargetId(resolveTargetId(ref, ref.type(), context));
                entityRef.setDisplayText(displayOf(ref));
                entityRef.setSortOrder(order++);
                saved.getReferences().add(entityRef);
            }
        }
        trapRepository.save(saved);
    }

    private void resolveHazardRefs(HazardDto dto, Hazard hazard, CampaignImportContext context) {
        Hazard saved = hazardRepository.findDetailedById(hazard.getId()).orElse(hazard);
        saved.getReferences().clear();
        int order = 0;
        if (dto.conditionRefs() != null) {
            for (ContentReference ref : dto.conditionRefs()) {
                if (ref == null) continue;
                ThreatReference entityRef = new ThreatReference();
                entityRef.setHazard(saved);
                entityRef.setRole(ThreatReferenceRole.CONDITION);
                entityRef.setTargetType(CampaignContentType.CONDITION);
                entityRef.setTargetId(resolveTargetId(ref, CampaignContentType.CONDITION, context));
                entityRef.setDisplayText(displayOf(ref));
                entityRef.setSortOrder(order++);
                saved.getReferences().add(entityRef);
            }
        }
        if (dto.salvageItemRefs() != null) {
            for (ContentReference ref : dto.salvageItemRefs()) {
                if (ref == null) continue;
                ThreatReference entityRef = new ThreatReference();
                entityRef.setHazard(saved);
                entityRef.setRole(ThreatReferenceRole.SALVAGE_ITEM);
                entityRef.setTargetType(ref.type());
                entityRef.setTargetId(resolveTargetId(ref, ref.type(), context));
                entityRef.setDisplayText(displayOf(ref));
                entityRef.setSortOrder(order++);
                saved.getReferences().add(entityRef);
            }
        }
        hazardRepository.save(saved);
    }

    private UUID resolveTargetId(ContentReference ref, CampaignContentType expected,
                                 CampaignImportContext context) {
        if (ref.scope() == ContentReference.Scope.CATALOG) {
            return resolveCatalogId(ref);
        }
        Object entity = context.require(ref, expected, Object.class);
        return entityId(entity);
    }

    private UUID resolveCatalogId(ContentReference ref) {
        return switch (ref.type()) {
            case CONDITION -> conditionRepository.findBySourceAndSourceKey(ContentSource.SRD, ref.sourceKey())
                    .map(Condition::getId).orElse(null);
            case EQUIPMENT_ITEM -> equipmentItemRepository
                    .findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.SRD, ref.sourceKey())
                    .map(EquipmentItem::getId).orElse(null);
            case MAGIC_ITEM -> magicItemRepository
                    .findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.SRD, ref.sourceKey())
                    .map(MagicItem::getId).orElse(null);
            default -> null;
        };
    }

    private static ThreatCheck toThreatCheck(ThreatCheckDto dto) {
        if (dto == null) return null;
        ThreatCheck check = new ThreatCheck();
        if (dto.mode() != null) {
            check.setMode(ThreatCheckMode.valueOf(dto.mode()));
        }
        check.setAbility(dto.ability());
        check.setSkill(dto.skill());
        check.setDc(dto.dc());
        return check;
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

    private static String displayOf(ContentReference ref) {
        if (ref == null) return null;
        return ref.scope() == ContentReference.Scope.CATALOG ? ref.sourceKey() : ref.key();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
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
