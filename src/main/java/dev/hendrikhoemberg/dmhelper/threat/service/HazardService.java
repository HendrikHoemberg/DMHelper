package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.LibraryReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.MapThreatPinRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheck;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class HazardService {

    private final HazardRepository repository;
    private final CampaignRepository campaignRepository;
    private final CustomContentSupport customContentSupport;
    private final ThreatValidator validator;
    private final ThreatReferenceResolver referenceResolver;
    private final ThreatDependencyService dependencyService;
    private final LibraryReferenceCleaner libraryReferenceCleaner;
    private final SceneSectionRepository sceneSectionRepository;
    private final CombatantRepository combatantRepository;
    private final MapThreatPinRepository mapThreatPinRepository;

    public HazardService(HazardRepository repository,
                         CampaignRepository campaignRepository,
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
    public Hazard findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Hazard not found: " + id));
    }

    @Transactional(readOnly = true)
    public Hazard findDetailedById(UUID id) {
        return repository.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Hazard not found: " + id));
    }

    @Transactional(readOnly = true)
    public Hazard requireVisible(UUID id, UUID campaignIdOrNull) {
        return (Hazard) referenceResolver.requireVisible(ThreatKind.HAZARD, id, campaignIdOrNull);
    }

    @Transactional(readOnly = true)
    public List<Hazard> findVisible(UUID campaignIdOrNull) {
        return repository.findVisibleByCampaignId(campaignIdOrNull);
    }

    public Hazard create(UUID campaignIdOrNull, HazardWrite write, ContentProvenance provenance) {
        Campaign campaign = requireCampaign(campaignIdOrNull);
        validator.validateHazard(write, campaignIdOrNull);
        assertAvailableSourceKey(write.sourceKey(), campaignIdOrNull, null);

        Hazard hazard = new Hazard();
        hazard.setSource(ContentSource.CUSTOM);
        hazard.setCampaign(campaign);
        applyWrite(hazard, write, provenance);
        return repository.save(hazard);
    }

    public Hazard updateCustom(UUID id, HazardWrite write, ContentProvenance provenance) {
        Hazard hazard = findDetailedById(id);
        customContentSupport.assertCustom(hazard.getSource());
        UUID campaignIdOrNull = hazard.getCampaign() == null ? null : hazard.getCampaign().getId();
        validator.validateHazard(write, campaignIdOrNull);
        assertAvailableSourceKey(write.sourceKey(), campaignIdOrNull, id);

        hazard.getReferences().clear();
        hazard.getDamageTypes().clear();
        repository.flush();
        applyWrite(hazard, write, provenance);
        return repository.save(hazard);
    }

    public Hazard cloneAsCustom(UUID sourceId, UUID campaignIdOrNull, String newName) {
        Hazard original = findDetailedById(sourceId);
        Campaign campaign = requireCampaign(campaignIdOrNull);
        String cloneName = newName != null && !newName.isBlank()
                ? newName
                : original.getName() + " Copy";
        String cloneSourceKey = customContentSupport.slugify(cloneName);
        HazardWrite cloneWrite = copyWrite(original, cloneSourceKey, cloneName);
        validator.validateHazard(cloneWrite, campaignIdOrNull);
        assertAvailableSourceKey(cloneSourceKey, campaignIdOrNull, null);

        Hazard clone = new Hazard();
        clone.setSource(ContentSource.CUSTOM);
        clone.setCampaign(campaign);
        ContentProvenance provenance = original.getProvenance() != null
                ? original.getProvenance()
                : customContentSupport.defaultForSrdClone();
        applyWrite(clone, cloneWrite, provenance);
        return repository.save(clone);
    }

    public Hazard promoteToGlobal(UUID id) {
        Hazard hazard = findDetailedById(id);
        customContentSupport.assertCustom(hazard.getSource());

        for (var ref : hazard.getReferences()) {
            if (!referenceResolver.isVisibleToScope(ref.getTargetType(), ref.getTargetId(), null)) {
                throw new IllegalArgumentException(
                        "Cannot promote: reference to " + ref.getTargetType() + " " + ref.getTargetId()
                                + " would cross scope");
            }
        }

        if (hazard.getCampaign() != null) {
            UUID oldCampaignId = hazard.getCampaign().getId();
            hazard.setCampaign(null);
            libraryReferenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.HAZARD, id);
        }
        return repository.save(hazard);
    }

    @Transactional(readOnly = true)
    public ThreatDeletionImpact deletionImpact(UUID id) {
        findById(id);
        return dependencyService.computeDeletionImpact(ThreatKind.HAZARD, id);
    }

    public void deleteCustom(UUID id, boolean confirmed) {
        Hazard hazard = findById(id);
        customContentSupport.assertCustom(hazard.getSource());

        ThreatDeletionImpact impact = dependencyService.computeDeletionImpact(ThreatKind.HAZARD, id);
        if (!confirmed && impact.hasDependents()) {
            throw new IllegalArgumentException(
                    "Hazard has " + impact.dependencies().size()
                            + " dependent(s); set confirmed=true to proceed");
        }

        if (confirmed || impact.hasDependents()) {
            for (var section : sceneSectionRepository.findByThreatKindAndThreatId(ThreatKind.HAZARD, id)) {
                section.setThreatKind(null);
                section.setThreatId(null);
            }
            for (var combatant : combatantRepository.findByThreatKindAndThreatId(ThreatKind.HAZARD, id)) {
                combatant.setThreatKind(null);
                combatant.setThreatId(null);
            }
            mapThreatPinRepository.deleteByThreatKindAndThreatId(ThreatKind.HAZARD, id);
        }

        if (hazard.getCampaign() != null) {
            libraryReferenceCleaner.deletePackageKey(
                    hazard.getCampaign().getId(), CampaignContentType.HAZARD, id);
        }

        repository.delete(hazard);
    }

    private void applyWrite(Hazard hazard, HazardWrite write, ContentProvenance provenance) {
        hazard.setName(write.name());
        if (write.sourceKey() != null && !write.sourceKey().isBlank()) {
            hazard.setSourceKey(write.sourceKey());
        } else if (hazard.getSourceKey() == null) {
            hazard.setSourceKey(customContentSupport.slugify(write.name()));
        }
        hazard.setDescription(write.description());
        hazard.setSeverity(write.severity());
        hazard.setMinLevel(write.minLevel());
        hazard.setMaxLevel(write.maxLevel());
        hazard.setExposureMode(write.exposureMode());
        hazard.setExposureText(write.exposureText());
        hazard.setAreaHint(write.areaHint());
        hazard.setCheck(toThreatCheck(write.check()));
        hazard.setDamageExpression(write.damageExpression());
        hazard.getDamageTypes().clear();
        if (write.damageTypes() != null) {
            hazard.getDamageTypes().addAll(write.damageTypes());
        }
        hazard.setEscalationText(write.escalationText());
        hazard.setEndingConditions(write.endingConditions());

        if (provenance != null) {
            hazard.setProvenance(provenance);
        } else if (hazard.getProvenance() == null) {
            hazard.setProvenance(customContentSupport.defaultForCreate(null));
        }

        hazard.getReferences().clear();
        if (write.references() != null) {
            int sortOrder = 0;
            for (ThreatReferenceWrite refWrite : write.references()) {
                ThreatReference ref = new ThreatReference();
                ref.setHazard(hazard);
                ref.setRole(refWrite.role());
                ref.setTargetType(refWrite.targetType());
                ref.setTargetId(refWrite.targetId());
                ref.setDisplayText(refWrite.displayText());
                ref.setSortOrder(sortOrder++);
                hazard.getReferences().add(ref);
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

    private HazardWrite copyWrite(Hazard original, String sourceKey, String name) {
        List<ThreatReferenceWrite> references = new ArrayList<>();
        for (ThreatReference ref : original.getReferences()) {
            references.add(new ThreatReferenceWrite(
                    ref.getRole(), ref.getTargetType(), ref.getTargetId(), ref.getDisplayText()));
        }
        ThreatCheckWrite check = original.getCheck() == null ? null
                : new ThreatCheckWrite(
                original.getCheck().getMode(), original.getCheck().getAbility(),
                original.getCheck().getSkill(), original.getCheck().getDc());
        return new HazardWrite(
                sourceKey, name, original.getDescription(), original.getSeverity(),
                original.getMinLevel(), original.getMaxLevel(), original.getExposureMode(),
                original.getExposureText(), original.getAreaHint(), check,
                original.getDamageExpression(),
                original.getDamageTypes() == null ? List.of() : List.copyOf(original.getDamageTypes()),
                original.getEscalationText(), original.getEndingConditions(), references);
    }
}
