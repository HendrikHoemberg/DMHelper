package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.Feat;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FeatService {

    private final FeatRepository repository;
    private final CampaignRepository campaignRepository;
    private final CustomContentSupport customContentSupport;
    private final LibraryReferenceCleaner referenceCleaner;

    public FeatService(FeatRepository repository,
                       CampaignRepository campaignRepository,
                       CustomContentSupport customContentSupport,
                       LibraryReferenceCleaner referenceCleaner) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.customContentSupport = customContentSupport;
        this.referenceCleaner = referenceCleaner;
    }

    public record FeatWrite(
        String name, String category, String prerequisite, String benefit,
        String sourceKey
    ) {}

    public List<Feat> search(String search, String category) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("category")), cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<Feat> search(ContentSource source, UUID campaignId, String text) {
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

    public List<Feat> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public Feat findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Feat not found: " + id));
    }

    public List<Feat> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional
    public Feat createCustom(UUID campaignIdOrNull, FeatWrite request, ContentProvenance provenanceOrNull) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        Feat feat = new Feat();
        feat.setSource(ContentSource.CUSTOM);
        if (campaignIdOrNull != null) {
            campaignRepository.findById(campaignIdOrNull).ifPresent(feat::setCampaign);
        }
        feat.setName(request.name());
        if (request.category() != null) feat.setCategory(request.category());
        if (request.prerequisite() != null) feat.setPrerequisite(request.prerequisite());
        if (request.benefit() != null) feat.setBenefit(request.benefit());
        if (request.sourceKey() != null && !request.sourceKey().isBlank()) {
            feat.setSourceKey(request.sourceKey());
        } else {
            feat.setSourceKey(customContentSupport.slugify(request.name()));
        }
        if (provenanceOrNull != null) {
            feat.setProvenance(provenanceOrNull);
        }
        return repository.save(feat);
    }

    @Transactional
    public Feat updateCustom(UUID id, FeatWrite request, ContentProvenance provenanceOrNull) {
        Feat feat = findById(id);
        customContentSupport.assertCustom(feat.getSource());
        feat.setName(request.name());
        if (request.category() != null) feat.setCategory(request.category());
        if (request.prerequisite() != null) feat.setPrerequisite(request.prerequisite());
        if (request.benefit() != null) feat.setBenefit(request.benefit());
        if (provenanceOrNull != null) {
            feat.setProvenance(provenanceOrNull);
        }
        return repository.save(feat);
    }

    @Transactional
    public Feat cloneAsCustom(UUID sourceId, UUID targetCampaignIdOrNull, String newName) {
        Feat original = findById(sourceId);
        Feat clone = new Feat();
        clone.setSource(ContentSource.CUSTOM);
        if (targetCampaignIdOrNull != null) {
            campaignRepository.findById(targetCampaignIdOrNull).ifPresent(clone::setCampaign);
        }
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setSourceKey(customContentSupport.slugify(clone.getName()));
        clone.setCategory(original.getCategory());
        clone.setPrerequisite(original.getPrerequisite());
        clone.setBenefit(original.getBenefit());
        if (original.getProvenance() != null) {
            clone.setProvenance(original.getProvenance());
        } else {
            clone.setProvenance(customContentSupport.defaultForSrdClone());
        }
        return repository.save(clone);
    }

    @Transactional
    public Feat promoteToGlobal(UUID id) {
        Feat feat = findById(id);
        customContentSupport.assertCustom(feat.getSource());
        if (feat.getCampaign() != null) {
            UUID oldCampaignId = feat.getCampaign().getId();
            feat.setCampaign(null);
            referenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.FEAT, id);
        }
        return repository.save(feat);
    }

    @Transactional
    public void deleteCustom(UUID id) {
        Feat feat = findById(id);
        customContentSupport.assertCustom(feat.getSource());
        int refs = referenceCleaner.countFeatSourceKeyReferences(feat.getSourceKey());
        if (refs > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete feat: referenced by " + refs + " character sheet(s)");
        }
        if (feat.getCampaign() != null) {
            referenceCleaner.deletePackageKey(feat.getCampaign().getId(), CampaignContentType.FEAT, id);
        }
        repository.delete(feat);
    }
}
