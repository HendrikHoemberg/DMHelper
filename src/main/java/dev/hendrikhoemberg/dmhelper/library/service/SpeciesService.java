package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SpeciesService {

    private final SpeciesRepository repository;
    private final CampaignRepository campaignRepository;
    private final CustomContentSupport customContentSupport;
    private final LibraryReferenceCleaner referenceCleaner;

    public SpeciesService(SpeciesRepository repository,
                          CampaignRepository campaignRepository,
                          CustomContentSupport customContentSupport,
                          LibraryReferenceCleaner referenceCleaner) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.customContentSupport = customContentSupport;
        this.referenceCleaner = referenceCleaner;
    }

    public record SpeciesWrite(
        String name, String size, String speed, String traits,
        String description, String sourceKey
    ) {}

    public List<Species> search(String search) {
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

    public List<Species> search(ContentSource source, UUID campaignId, String text) {
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

    public List<Species> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public Species findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Species not found: " + id));
    }

    public List<Species> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional
    public Species createCustom(UUID campaignIdOrNull, SpeciesWrite request, ContentProvenance provenanceOrNull) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        Species species = new Species();
        species.setSource(ContentSource.CUSTOM);
        if (campaignIdOrNull != null) {
            campaignRepository.findById(campaignIdOrNull).ifPresent(species::setCampaign);
        }
        species.setName(request.name());
        if (request.size() != null) species.setSize(request.size());
        if (request.speed() != null) species.setSpeed(request.speed());
        if (request.traits() != null) species.setTraits(request.traits());
        if (request.description() != null) species.setDescription(request.description());
        if (request.sourceKey() != null && !request.sourceKey().isBlank()) {
            species.setSourceKey(request.sourceKey());
        } else {
            species.setSourceKey(customContentSupport.slugify(request.name()));
        }
        if (provenanceOrNull != null) {
            species.setProvenance(provenanceOrNull);
        }
        return repository.save(species);
    }

    @Transactional
    public Species updateCustom(UUID id, SpeciesWrite request, ContentProvenance provenanceOrNull) {
        Species species = findById(id);
        customContentSupport.assertCustom(species.getSource());
        species.setName(request.name());
        if (request.size() != null) species.setSize(request.size());
        if (request.speed() != null) species.setSpeed(request.speed());
        if (request.traits() != null) species.setTraits(request.traits());
        if (request.description() != null) species.setDescription(request.description());
        if (provenanceOrNull != null) {
            species.setProvenance(provenanceOrNull);
        }
        return repository.save(species);
    }

    @Transactional
    public Species cloneAsCustom(UUID sourceId, UUID targetCampaignIdOrNull, String newName) {
        Species original = findById(sourceId);
        Species clone = new Species();
        clone.setSource(ContentSource.CUSTOM);
        if (targetCampaignIdOrNull != null) {
            campaignRepository.findById(targetCampaignIdOrNull).ifPresent(clone::setCampaign);
        }
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setSourceKey(customContentSupport.slugify(clone.getName()));
        clone.setSize(original.getSize());
        clone.setSpeed(original.getSpeed());
        clone.setTraits(original.getTraits());
        clone.setDescription(original.getDescription());
        if (original.getProvenance() != null) {
            clone.setProvenance(original.getProvenance());
        } else {
            clone.setProvenance(customContentSupport.defaultForSrdClone());
        }
        return repository.save(clone);
    }

    @Transactional
    public Species promoteToGlobal(UUID id) {
        Species species = findById(id);
        customContentSupport.assertCustom(species.getSource());
        if (species.getCampaign() != null) {
            UUID oldCampaignId = species.getCampaign().getId();
            species.setCampaign(null);
            referenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.SPECIES, id);
        }
        return repository.save(species);
    }

    @Transactional
    public void deleteCustom(UUID id) {
        Species species = findById(id);
        customContentSupport.assertCustom(species.getSource());
        int refs = referenceCleaner.countSpeciesReferences(id);
        if (refs > 0) {
            throw new IllegalArgumentException(
                "Cannot delete species: referenced by " + refs + " character sheet(s)");
        }
        if (species.getCampaign() != null) {
            referenceCleaner.deletePackageKey(species.getCampaign().getId(), CampaignContentType.SPECIES, id);
        }
        repository.delete(species);
    }
}
