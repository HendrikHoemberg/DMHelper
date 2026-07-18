package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.Condition;
import dev.hendrikhoemberg.dmhelper.library.data.ConditionRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ConditionService {

    private final ConditionRepository repository;
    private final CampaignRepository campaignRepository;
    private final CustomContentSupport customContentSupport;
    private final LibraryReferenceCleaner referenceCleaner;

    public ConditionService(ConditionRepository repository,
                            CampaignRepository campaignRepository,
                            CustomContentSupport customContentSupport,
                            LibraryReferenceCleaner referenceCleaner) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.customContentSupport = customContentSupport;
        this.referenceCleaner = referenceCleaner;
    }

    public record ConditionWrite(
        String name, String description, String sourceKey
    ) {}

    public List<Condition> search(String search) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<Condition> search(ContentSource source, UUID campaignId, String text) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (campaignId != null) {
                predicates.add(cb.equal(root.get("campaign").get("id"), campaignId));
            }
            if (text != null && !text.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + text.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<Condition> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public Condition findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Condition not found: " + id));
    }

    public List<Condition> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional
    public Condition createCustom(UUID campaignIdOrNull, ConditionWrite request, ContentProvenance provenanceOrNull) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        Condition condition = new Condition();
        condition.setSource(ContentSource.CUSTOM);
        if (campaignIdOrNull != null) {
            campaignRepository.findById(campaignIdOrNull).ifPresent(condition::setCampaign);
        }
        condition.setName(request.name());
        condition.setDescription(request.description());
        if (request.sourceKey() != null && !request.sourceKey().isBlank()) {
            condition.setSourceKey(request.sourceKey());
        } else {
            condition.setSourceKey(customContentSupport.slugify(request.name()));
        }
        if (provenanceOrNull != null) {
            condition.setProvenance(provenanceOrNull);
        }
        return repository.save(condition);
    }

    @Transactional
    public Condition updateCustom(UUID id, ConditionWrite request, ContentProvenance provenanceOrNull) {
        Condition condition = findById(id);
        customContentSupport.assertCustom(condition.getSource());
        condition.setName(request.name());
        if (request.description() != null) condition.setDescription(request.description());
        if (provenanceOrNull != null) {
            condition.setProvenance(provenanceOrNull);
        }
        return repository.save(condition);
    }

    @Transactional
    public Condition cloneAsCustom(UUID sourceId, UUID targetCampaignIdOrNull, String newName) {
        Condition original = findById(sourceId);
        Condition clone = new Condition();
        clone.setSource(ContentSource.CUSTOM);
        if (targetCampaignIdOrNull != null) {
            campaignRepository.findById(targetCampaignIdOrNull).ifPresent(clone::setCampaign);
        }
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setSourceKey(customContentSupport.slugify(clone.getName()));
        clone.setDescription(original.getDescription());
        if (original.getProvenance() != null) {
            clone.setProvenance(original.getProvenance());
        } else {
            clone.setProvenance(customContentSupport.defaultForSrdClone());
        }
        return repository.save(clone);
    }

    @Transactional
    public Condition promoteToGlobal(UUID id) {
        Condition condition = findById(id);
        customContentSupport.assertCustom(condition.getSource());
        if (condition.getCampaign() != null) {
            UUID oldCampaignId = condition.getCampaign().getId();
            condition.setCampaign(null);
            referenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.CONDITION, id);
        }
        return repository.save(condition);
    }

    @Transactional
    public void deleteCustom(UUID id) {
        Condition condition = findById(id);
        customContentSupport.assertCustom(condition.getSource());
        int refs = referenceCleaner.countThreatReferences(CampaignContentType.CONDITION, id);
        if (refs > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete condition: referenced by " + refs + " trap/hazard reference(s)");
        }
        if (condition.getCampaign() != null) {
            referenceCleaner.deletePackageKey(condition.getCampaign().getId(), CampaignContentType.CONDITION, id);
        }
        repository.delete(condition);
    }
}
