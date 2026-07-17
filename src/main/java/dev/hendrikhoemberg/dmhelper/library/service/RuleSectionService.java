package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSection;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSectionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RuleSectionService {

    private final RuleSectionRepository repository;
    private final CampaignRepository campaignRepository;
    private final CustomContentSupport customContentSupport;
    private final LibraryReferenceCleaner referenceCleaner;

    public RuleSectionService(RuleSectionRepository repository,
                              CampaignRepository campaignRepository,
                              CustomContentSupport customContentSupport,
                              LibraryReferenceCleaner referenceCleaner) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.customContentSupport = customContentSupport;
        this.referenceCleaner = referenceCleaner;
    }

    public record RuleSectionWrite(
        String name, String body, String parentKey, Integer sortOrder,
        String ruleset, Integer initialHeaderLevel, String sourceKey
    ) {}

    public List<RuleSection> search(String search, String ruleset) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (ruleset != null && !ruleset.isBlank()) {
                predicates.add(cb.equal(root.get("ruleset"), ruleset));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("ruleset")), cb.asc(root.get("sortOrder")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<RuleSection> search(ContentSource source, UUID campaignId, String text) {
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
            query.orderBy(cb.asc(root.get("ruleset")), cb.asc(root.get("sortOrder")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<RuleSection> findAll() {
        return repository.findAllByOrderBySortOrderAsc();
    }

    public RuleSection findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("RuleSection not found: " + id));
    }

    public List<RuleSection> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional
    public RuleSection createCustom(UUID campaignIdOrNull, RuleSectionWrite request, ContentProvenance provenanceOrNull) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        RuleSection section = new RuleSection();
        section.setSource(ContentSource.CUSTOM);
        if (campaignIdOrNull != null) {
            campaignRepository.findById(campaignIdOrNull).ifPresent(section::setCampaign);
        }
        section.setName(request.name());
        if (request.body() != null) section.setBody(request.body());
        if (request.parentKey() != null) section.setParentKey(request.parentKey());
        section.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        section.setRuleset(request.ruleset() != null && !request.ruleset().isBlank() ? request.ruleset() : "CUSTOM");
        section.setInitialHeaderLevel(request.initialHeaderLevel() != null ? request.initialHeaderLevel() : 1);
        if (request.sourceKey() != null && !request.sourceKey().isBlank()) {
            section.setSourceKey(request.sourceKey());
        } else {
            section.setSourceKey(customContentSupport.slugify(request.name()));
        }
        if (provenanceOrNull != null) {
            section.setProvenance(provenanceOrNull);
        }
        return repository.save(section);
    }

    @Transactional
    public RuleSection updateCustom(UUID id, RuleSectionWrite request, ContentProvenance provenanceOrNull) {
        RuleSection section = findById(id);
        customContentSupport.assertCustom(section.getSource());
        section.setName(request.name());
        if (request.body() != null) section.setBody(request.body());
        if (request.parentKey() != null) section.setParentKey(request.parentKey());
        if (request.sortOrder() != null) section.setSortOrder(request.sortOrder());
        if (request.ruleset() != null && !request.ruleset().isBlank()) section.setRuleset(request.ruleset());
        if (request.initialHeaderLevel() != null) section.setInitialHeaderLevel(request.initialHeaderLevel());
        if (provenanceOrNull != null) {
            section.setProvenance(provenanceOrNull);
        }
        return repository.save(section);
    }

    @Transactional
    public RuleSection cloneAsCustom(UUID sourceId, UUID targetCampaignIdOrNull, String newName) {
        RuleSection original = findById(sourceId);
        RuleSection clone = new RuleSection();
        clone.setSource(ContentSource.CUSTOM);
        if (targetCampaignIdOrNull != null) {
            campaignRepository.findById(targetCampaignIdOrNull).ifPresent(clone::setCampaign);
        }
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setSourceKey(customContentSupport.slugify(clone.getName()));
        clone.setBody(original.getBody());
        clone.setParentKey(original.getParentKey());
        clone.setSortOrder(original.getSortOrder());
        clone.setRuleset(original.getRuleset() != null ? original.getRuleset() : "CUSTOM");
        clone.setInitialHeaderLevel(original.getInitialHeaderLevel());
        if (original.getProvenance() != null) {
            clone.setProvenance(original.getProvenance());
        } else {
            clone.setProvenance(customContentSupport.defaultForSrdClone());
        }
        return repository.save(clone);
    }

    @Transactional
    public RuleSection promoteToGlobal(UUID id) {
        RuleSection section = findById(id);
        customContentSupport.assertCustom(section.getSource());
        if (section.getCampaign() != null) {
            UUID oldCampaignId = section.getCampaign().getId();
            section.setCampaign(null);
            referenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.RULE, id);
        }
        return repository.save(section);
    }

    @Transactional
    public void deleteCustom(UUID id) {
        RuleSection section = findById(id);
        customContentSupport.assertCustom(section.getSource());
        if (section.getCampaign() != null) {
            referenceCleaner.deletePackageKey(section.getCampaign().getId(), CampaignContentType.RULE, id);
        }
        repository.delete(section);
    }
}
