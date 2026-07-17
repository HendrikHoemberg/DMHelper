package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.Background;
import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
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
public class BackgroundService {

    private final BackgroundRepository repository;
    private final CampaignRepository campaignRepository;
    private final CustomContentSupport customContentSupport;
    private final LibraryReferenceCleaner referenceCleaner;

    public BackgroundService(BackgroundRepository repository,
                             CampaignRepository campaignRepository,
                             CustomContentSupport customContentSupport,
                             LibraryReferenceCleaner referenceCleaner) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.customContentSupport = customContentSupport;
        this.referenceCleaner = referenceCleaner;
    }

    public record BackgroundWrite(
        String name, String abilityScores, String featRef, String skills,
        String tools, String description, String equipment, String sourceKey
    ) {}

    public List<Background> search(String search) {
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

    public List<Background> search(ContentSource source, UUID campaignId, String text) {
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

    public List<Background> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public Background findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Background not found: " + id));
    }

    public List<Background> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional
    public Background createCustom(UUID campaignIdOrNull, BackgroundWrite request, ContentProvenance provenanceOrNull) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        Background bg = new Background();
        bg.setSource(ContentSource.CUSTOM);
        if (campaignIdOrNull != null) {
            campaignRepository.findById(campaignIdOrNull).ifPresent(bg::setCampaign);
        }
        bg.setName(request.name());
        if (request.abilityScores() != null) bg.setAbilityScores(request.abilityScores());
        if (request.featRef() != null) bg.setFeatRef(request.featRef());
        if (request.skills() != null) bg.setSkills(request.skills());
        if (request.tools() != null) bg.setTools(request.tools());
        if (request.description() != null) bg.setDescription(request.description());
        if (request.equipment() != null) bg.setEquipment(request.equipment());
        if (request.sourceKey() != null && !request.sourceKey().isBlank()) {
            bg.setSourceKey(request.sourceKey());
        } else {
            bg.setSourceKey(customContentSupport.slugify(request.name()));
        }
        if (provenanceOrNull != null) {
            bg.setProvenance(provenanceOrNull);
        }
        return repository.save(bg);
    }

    @Transactional
    public Background updateCustom(UUID id, BackgroundWrite request, ContentProvenance provenanceOrNull) {
        Background bg = findById(id);
        customContentSupport.assertCustom(bg.getSource());
        bg.setName(request.name());
        if (request.abilityScores() != null) bg.setAbilityScores(request.abilityScores());
        if (request.featRef() != null) bg.setFeatRef(request.featRef());
        if (request.skills() != null) bg.setSkills(request.skills());
        if (request.tools() != null) bg.setTools(request.tools());
        if (request.description() != null) bg.setDescription(request.description());
        if (request.equipment() != null) bg.setEquipment(request.equipment());
        if (provenanceOrNull != null) {
            bg.setProvenance(provenanceOrNull);
        }
        return repository.save(bg);
    }

    @Transactional
    public Background cloneAsCustom(UUID sourceId, UUID targetCampaignIdOrNull, String newName) {
        Background original = findById(sourceId);
        Background clone = new Background();
        clone.setSource(ContentSource.CUSTOM);
        if (targetCampaignIdOrNull != null) {
            campaignRepository.findById(targetCampaignIdOrNull).ifPresent(clone::setCampaign);
        }
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setSourceKey(customContentSupport.slugify(clone.getName()));
        clone.setAbilityScores(original.getAbilityScores());
        clone.setFeatRef(original.getFeatRef());
        clone.setSkills(original.getSkills());
        clone.setTools(original.getTools());
        clone.setDescription(original.getDescription());
        clone.setEquipment(original.getEquipment());
        if (original.getProvenance() != null) {
            clone.setProvenance(original.getProvenance());
        } else {
            clone.setProvenance(customContentSupport.defaultForSrdClone());
        }
        return repository.save(clone);
    }

    @Transactional
    public Background promoteToGlobal(UUID id) {
        Background bg = findById(id);
        customContentSupport.assertCustom(bg.getSource());
        if (bg.getCampaign() != null) {
            UUID oldCampaignId = bg.getCampaign().getId();
            bg.setCampaign(null);
            referenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.BACKGROUND, id);
        }
        return repository.save(bg);
    }

    @Transactional
    public void deleteCustom(UUID id) {
        Background bg = findById(id);
        customContentSupport.assertCustom(bg.getSource());
        int refs = referenceCleaner.countBackgroundReferences(id);
        if (refs > 0) {
            throw new IllegalArgumentException(
                "Cannot delete background: referenced by " + refs + " character sheet(s)");
        }
        if (bg.getCampaign() != null) {
            referenceCleaner.deletePackageKey(bg.getCampaign().getId(), CampaignContentType.BACKGROUND, id);
        }
        repository.delete(bg);
    }
}
