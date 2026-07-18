package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.LibraryReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheck;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReference;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapDisarmMethod;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPinRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TrapService {

    private final TrapRepository repository;
    private final CampaignRepository campaignRepository;
    private final StatBlockRepository statBlockRepository;
    private final CustomContentSupport customContentSupport;
    private final ThreatValidator validator;
    private final ThreatReferenceResolver referenceResolver;
    private final ThreatDependencyService dependencyService;
    private final LibraryReferenceCleaner libraryReferenceCleaner;
    private final SceneSectionRepository sceneSectionRepository;
    private final CombatantRepository combatantRepository;
    private final MapThreatPinRepository mapThreatPinRepository;

    public TrapService(TrapRepository repository,
                       CampaignRepository campaignRepository,
                       StatBlockRepository statBlockRepository,
                       CustomContentSupport customContentSupport,
                       ThreatValidator validator,
                       ThreatReferenceResolver referenceResolver,
                       ThreatDependencyService dependencyService,
                       LibraryReferenceCleaner libraryReferenceCleaner,
                       SceneSectionRepository sceneSectionRepository,
                       CombatantRepository combatantRepository,
                       MapThreatPinRepository mapThreatPinRepository) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.statBlockRepository = statBlockRepository;
        this.customContentSupport = customContentSupport;
        this.validator = validator;
        this.referenceResolver = referenceResolver;
        this.dependencyService = dependencyService;
        this.libraryReferenceCleaner = libraryReferenceCleaner;
        this.sceneSectionRepository = sceneSectionRepository;
        this.combatantRepository = combatantRepository;
        this.mapThreatPinRepository = mapThreatPinRepository;
    }

    @Transactional(readOnly = true)
    public Trap findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Trap not found: " + id));
    }

    @Transactional(readOnly = true)
    public Trap findDetailedById(UUID id) {
        Trap trap = repository.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Trap not found: " + id));
        // Force bag/association init so callers remain safe under open-in-view=false.
        hydrate(trap);
        return trap;
    }

    /** Touch lazy bags while the session is open (production has open-in-view=false). */
    static void hydrate(Trap trap) {
        trap.getDisarmMethods().size();
        trap.getDamageTypes().size();
        trap.getReferences().size();
        if (trap.getStatBlock() != null) {
            trap.getStatBlock().getName();
        }
        if (trap.getCampaign() != null) {
            trap.getCampaign().getId();
        }
    }

    @Transactional(readOnly = true)
    public Trap requireVisible(UUID id, UUID campaignIdOrNull) {
        return (Trap) referenceResolver.requireVisible(ThreatKind.TRAP, id, campaignIdOrNull);
    }

    @Transactional(readOnly = true)
    public List<Trap> findVisible(UUID campaignIdOrNull) {
        return repository.findVisibleByCampaignId(campaignIdOrNull);
    }

    public Trap create(UUID campaignIdOrNull, TrapWrite write, ContentProvenance provenance) {
        Campaign campaign = requireCampaign(campaignIdOrNull);
        validator.validateTrap(write, campaignIdOrNull);
        assertAvailableSourceKey(write.sourceKey(), campaignIdOrNull, null);

        Trap trap = new Trap();
        trap.setSource(ContentSource.CUSTOM);
        trap.setCampaign(campaign);
        applyWrite(trap, write, provenance);
        return repository.save(trap);
    }

    public Trap updateCustom(UUID id, TrapWrite write, ContentProvenance provenance) {
        Trap trap = findDetailedById(id);
        customContentSupport.assertCustom(trap.getSource());
        UUID campaignIdOrNull = trap.getCampaign() == null ? null : trap.getCampaign().getId();
        validator.validateTrap(write, campaignIdOrNull);
        assertAvailableSourceKey(write.sourceKey(), campaignIdOrNull, id);

        trap.getDisarmMethods().clear();
        trap.getReferences().clear();
        trap.getDamageTypes().clear();
        repository.flush();
        applyWrite(trap, write, provenance);
        return repository.save(trap);
    }

    public Trap cloneAsCustom(UUID sourceId, UUID campaignIdOrNull, String newName) {
        Trap original = findDetailedById(sourceId);
        Campaign campaign = requireCampaign(campaignIdOrNull);
        String cloneName = newName != null && !newName.isBlank()
                ? newName
                : original.getName() + " Copy";
        String cloneSourceKey = customContentSupport.slugify(cloneName);
        TrapWrite cloneWrite = copyWrite(original, cloneSourceKey, cloneName);
        validator.validateTrap(cloneWrite, campaignIdOrNull);
        assertAvailableSourceKey(cloneSourceKey, campaignIdOrNull, null);

        Trap clone = new Trap();
        clone.setSource(ContentSource.CUSTOM);
        clone.setCampaign(campaign);
        ContentProvenance provenance = original.getProvenance() != null
                ? original.getProvenance()
                : customContentSupport.defaultForSrdClone();
        applyWrite(clone, cloneWrite, provenance);
        return repository.save(clone);
    }

    public Trap promoteToGlobal(UUID id) {
        Trap trap = findDetailedById(id);
        customContentSupport.assertCustom(trap.getSource());

        for (var ref : trap.getReferences()) {
            if (!referenceResolver.isVisibleToScope(ref.getTargetType(), ref.getTargetId(), null)) {
                throw new IllegalArgumentException(
                        "Cannot promote: reference to " + ref.getTargetType() + " " + ref.getTargetId()
                                + " would cross scope");
            }
        }
        if (trap.getStatBlock() != null
                && !referenceResolver.isVisibleToScope(
                CampaignContentType.STATBLOCK, trap.getStatBlock().getId(), null)) {
            throw new IllegalArgumentException(
                    "Cannot promote: stat block reference would cross scope");
        }

        if (trap.getCampaign() != null) {
            UUID oldCampaignId = trap.getCampaign().getId();
            trap.setCampaign(null);
            libraryReferenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.TRAP, id);
        }
        return repository.save(trap);
    }

    @Transactional(readOnly = true)
    public ThreatDeletionImpact deletionImpact(UUID id) {
        findById(id);
        return dependencyService.computeDeletionImpact(ThreatKind.TRAP, id);
    }

    public void deleteCustom(UUID id, boolean confirmed) {
        Trap trap = findById(id);
        customContentSupport.assertCustom(trap.getSource());

        ThreatDeletionImpact impact = dependencyService.computeDeletionImpact(ThreatKind.TRAP, id);
        if (!confirmed && impact.hasDependents()) {
            throw new IllegalArgumentException(
                    "Trap has " + impact.dependencies().size()
                            + " dependent(s); set confirmed=true to proceed");
        }

        if (confirmed || impact.hasDependents()) {
            for (var section : sceneSectionRepository.findByThreatKindAndThreatId(ThreatKind.TRAP, id)) {
                section.setThreatKind(null);
                section.setThreatId(null);
            }
            for (var combatant : combatantRepository.findByThreatKindAndThreatId(ThreatKind.TRAP, id)) {
                combatant.setThreatKind(null);
                combatant.setThreatId(null);
            }
            mapThreatPinRepository.deleteByThreatKindAndThreatId(ThreatKind.TRAP, id);
        }

        if (trap.getCampaign() != null) {
            libraryReferenceCleaner.deletePackageKey(
                    trap.getCampaign().getId(), CampaignContentType.TRAP, id);
        }

        repository.delete(trap);
    }

    private void applyWrite(Trap trap, TrapWrite write, ContentProvenance provenance) {
        trap.setName(write.name());
        if (write.sourceKey() != null && !write.sourceKey().isBlank()) {
            trap.setSourceKey(write.sourceKey());
        } else if (trap.getSourceKey() == null) {
            trap.setSourceKey(customContentSupport.slugify(write.name()));
        }
        trap.setDescription(write.description());
        trap.setSeverity(write.severity());
        trap.setMinLevel(write.minLevel());
        trap.setMaxLevel(write.maxLevel());
        trap.setTriggerDescription(write.triggerDescription());
        trap.setTriggerAreaHint(write.triggerAreaHint());
        trap.setDetectionPassiveThreshold(write.detectionPassiveThreshold());
        trap.setDetectionCheck(toThreatCheck(write.detectionCheck()));
        trap.setAttackBonus(write.attackBonus());
        trap.setSave(toThreatCheck(write.save()));
        trap.setDamageExpression(write.damageExpression());
        trap.getDamageTypes().clear();
        if (write.damageTypes() != null) {
            trap.getDamageTypes().addAll(write.damageTypes());
        }
        trap.setAdditionalEffect(write.additionalEffect());
        trap.setResetMode(write.resetMode() != null ? write.resetMode() : ThreatResetMode.NONE);
        trap.setResetTiming(write.resetTiming());
        trap.setCountermeasureNotes(write.countermeasureNotes());

        if (write.statBlockId() != null) {
            StatBlock sb = statBlockRepository.findById(write.statBlockId())
                    .orElseThrow(() -> new NotFoundException("StatBlock not found: " + write.statBlockId()));
            trap.setStatBlock(sb);
        } else {
            trap.setStatBlock(null);
        }

        if (provenance != null) {
            trap.setProvenance(provenance);
        } else if (trap.getProvenance() == null) {
            trap.setProvenance(customContentSupport.defaultForCreate(null));
        }

        trap.getDisarmMethods().clear();
        if (write.disarmMethods() != null) {
            int sortOrder = 0;
            for (TrapDisarmMethodWrite methodWrite : write.disarmMethods()) {
                TrapDisarmMethod method = new TrapDisarmMethod();
                method.setTrap(trap);
                method.setMethodKey(methodWrite.key());
                method.setLabel(methodWrite.label());
                method.setAbility(methodWrite.ability());
                method.setSkill(methodWrite.skill());
                method.setTool(methodWrite.tool());
                method.setDc(methodWrite.dc());
                method.setFailureConsequence(methodWrite.failureConsequence());
                method.setSortOrder(methodWrite.sortOrder() != 0 ? methodWrite.sortOrder() : sortOrder);
                sortOrder++;
                trap.getDisarmMethods().add(method);
            }
        }

        trap.getReferences().clear();
        if (write.references() != null) {
            int sortOrder = 0;
            for (ThreatReferenceWrite refWrite : write.references()) {
                ThreatReference ref = new ThreatReference();
                ref.setTrap(trap);
                ref.setRole(refWrite.role());
                ref.setTargetType(refWrite.targetType());
                ref.setTargetId(refWrite.targetId());
                ref.setDisplayText(refWrite.displayText());
                ref.setSortOrder(sortOrder++);
                trap.getReferences().add(ref);
            }
        }
    }

    private static ThreatCheck toThreatCheck(ThreatCheckWrite write) {
        if (write == null) {
            return null;
        }
        boolean empty = write.mode() == null
                && (write.ability() == null || write.ability().isBlank())
                && (write.skill() == null || write.skill().isBlank())
                && write.dc() == null;
        if (empty) {
            return null;
        }
        ThreatCheck check = new ThreatCheck();
        check.setMode(write.mode());
        check.setAbility(write.ability());
        check.setSkill(write.skill());
        check.setDc(write.dc());
        return check;
    }

    private Campaign requireCampaign(UUID campaignIdOrNull) {
        if (campaignIdOrNull == null) {
            return null;
        }
        return campaignRepository.findById(campaignIdOrNull)
                .orElseThrow(() -> new NotFoundException("Campaign not found: " + campaignIdOrNull));
    }

    private void assertAvailableSourceKey(String sourceKey, UUID campaignIdOrNull, UUID currentIdOrNull) {
        customContentSupport.assertAvailableSourceKey(
                sourceKey,
                campaignIdOrNull,
                key -> repository.existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource.SRD, key),
                (source, key) -> currentIdOrNull == null
                        ? repository.existsBySourceAndSourceKeyAndCampaignIsNull(source, key)
                        : repository.existsBySourceAndSourceKeyAndCampaignIsNullAndIdNot(
                                source, key, currentIdOrNull),
                (campaignId, key) -> currentIdOrNull == null
                        ? repository.existsByCampaignIdAndSourceKey(campaignId, key)
                        : repository.existsByCampaignIdAndSourceKeyAndIdNot(
                                campaignId, key, currentIdOrNull));
    }

    private TrapWrite copyWrite(Trap original, String sourceKey, String name) {
        List<TrapDisarmMethodWrite> methods = new ArrayList<>();
        for (TrapDisarmMethod method : original.getDisarmMethods()) {
            methods.add(new TrapDisarmMethodWrite(
                    method.getMethodKey(), method.getLabel(), method.getAbility(),
                    method.getSkill(), method.getTool(), method.getDc(),
                    method.getFailureConsequence(), method.getSortOrder()));
        }
        List<ThreatReferenceWrite> references = new ArrayList<>();
        for (ThreatReference ref : original.getReferences()) {
            references.add(new ThreatReferenceWrite(
                    ref.getRole(), ref.getTargetType(), ref.getTargetId(), ref.getDisplayText()));
        }
        ThreatCheckWrite detection = fromThreatCheck(original.getDetectionCheck());
        ThreatCheckWrite save = fromThreatCheck(original.getSave());
        return new TrapWrite(
                sourceKey, name, original.getDescription(), original.getSeverity(),
                original.getMinLevel(), original.getMaxLevel(),
                original.getTriggerDescription(), original.getTriggerAreaHint(),
                original.getDetectionPassiveThreshold(), detection,
                methods, original.getAttackBonus(), save,
                original.getDamageExpression(),
                original.getDamageTypes() == null ? List.of() : List.copyOf(original.getDamageTypes()),
                original.getAdditionalEffect(), original.getResetMode(), original.getResetTiming(),
                original.getStatBlock() != null ? original.getStatBlock().getId() : null,
                original.getCountermeasureNotes(), references);
    }

    private static ThreatCheckWrite fromThreatCheck(ThreatCheck check) {
        if (check == null) {
            return null;
        }
        return new ThreatCheckWrite(check.getMode(), check.getAbility(), check.getSkill(), check.getDc());
    }
}
