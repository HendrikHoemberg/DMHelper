package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CharacterClassService {

    private final CharacterClassRepository repository;
    private final CampaignRepository campaignRepository;
    private final CustomContentSupport customContentSupport;
    private final LibraryReferenceCleaner referenceCleaner;

    public CharacterClassService(CharacterClassRepository repository,
                                 CampaignRepository campaignRepository,
                                 CustomContentSupport customContentSupport,
                                 LibraryReferenceCleaner referenceCleaner) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.customContentSupport = customContentSupport;
        this.referenceCleaner = referenceCleaner;
    }

    public record CharacterClassWrite(
        String name, String hitDie, String savingThrows, String features,
        String spellcasting, String subclassOf, String description,
        String proficiencies, String sourceKey
    ) {}

    public List<CharacterClass> search(String search) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("subclassOf")), cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<CharacterClass> search(ContentSource source, UUID campaignId, String text) {
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

    public List<CharacterClass> findBaseClasses() {
        return repository.findBySubclassOfIsNullOrderByNameAsc();
    }

    public List<CharacterClass> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public Optional<CharacterClass> findBySourceKey(String sourceKey) {
        return repository.findBySourceKey(sourceKey);
    }

    public CharacterClass findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CharacterClass not found: " + id));
    }

    public List<CharacterClass> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional
    public CharacterClass createCustom(UUID campaignIdOrNull, CharacterClassWrite request, ContentProvenance provenanceOrNull) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        CharacterClass clazz = new CharacterClass();
        clazz.setSource(ContentSource.CUSTOM);
        if (campaignIdOrNull != null) {
            campaignRepository.findById(campaignIdOrNull).ifPresent(clazz::setCampaign);
        }
        clazz.setName(request.name());
        if (request.hitDie() != null) clazz.setHitDie(request.hitDie());
        if (request.savingThrows() != null) clazz.setSavingThrows(request.savingThrows());
        if (request.features() != null) clazz.setFeatures(request.features());
        if (request.spellcasting() != null) clazz.setSpellcasting(request.spellcasting());
        if (request.subclassOf() != null) clazz.setSubclassOf(request.subclassOf());
        if (request.description() != null) clazz.setDescription(request.description());
        if (request.proficiencies() != null) clazz.setProficiencies(request.proficiencies());
        if (request.sourceKey() != null && !request.sourceKey().isBlank()) {
            clazz.setSourceKey(request.sourceKey());
        } else {
            clazz.setSourceKey(customContentSupport.slugify(request.name()));
        }
        if (provenanceOrNull != null) {
            clazz.setProvenance(provenanceOrNull);
        }
        return repository.save(clazz);
    }

    @Transactional
    public CharacterClass updateCustom(UUID id, CharacterClassWrite request, ContentProvenance provenanceOrNull) {
        CharacterClass clazz = findById(id);
        customContentSupport.assertCustom(clazz.getSource());
        clazz.setName(request.name());
        if (request.hitDie() != null) clazz.setHitDie(request.hitDie());
        if (request.savingThrows() != null) clazz.setSavingThrows(request.savingThrows());
        if (request.features() != null) clazz.setFeatures(request.features());
        if (request.spellcasting() != null) clazz.setSpellcasting(request.spellcasting());
        if (request.subclassOf() != null) clazz.setSubclassOf(request.subclassOf());
        if (request.description() != null) clazz.setDescription(request.description());
        if (request.proficiencies() != null) clazz.setProficiencies(request.proficiencies());
        if (provenanceOrNull != null) {
            clazz.setProvenance(provenanceOrNull);
        }
        return repository.save(clazz);
    }

    @Transactional
    public CharacterClass cloneAsCustom(UUID sourceId, UUID targetCampaignIdOrNull, String newName) {
        CharacterClass original = findById(sourceId);
        CharacterClass clone = new CharacterClass();
        clone.setSource(ContentSource.CUSTOM);
        if (targetCampaignIdOrNull != null) {
            campaignRepository.findById(targetCampaignIdOrNull).ifPresent(clone::setCampaign);
        }
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setSourceKey(customContentSupport.slugify(clone.getName()));
        clone.setHitDie(original.getHitDie());
        clone.setSavingThrows(original.getSavingThrows());
        clone.setFeatures(original.getFeatures());
        clone.setSpellcasting(original.getSpellcasting());
        clone.setSubclassOf(original.getSubclassOf());
        clone.setDescription(original.getDescription());
        clone.setProficiencies(original.getProficiencies());
        if (original.getProvenance() != null) {
            clone.setProvenance(original.getProvenance());
        } else {
            clone.setProvenance(customContentSupport.defaultForSrdClone());
        }
        return repository.save(clone);
    }

    @Transactional
    public CharacterClass promoteToGlobal(UUID id) {
        CharacterClass clazz = findById(id);
        customContentSupport.assertCustom(clazz.getSource());
        if (clazz.getCampaign() != null) {
            UUID oldCampaignId = clazz.getCampaign().getId();
            clazz.setCampaign(null);
            referenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.CLASS, id);
        }
        return repository.save(clazz);
    }

    @Transactional
    public void deleteCustom(UUID id) {
        CharacterClass clazz = findById(id);
        customContentSupport.assertCustom(clazz.getSource());
        int refs = referenceCleaner.countClassSourceKeyReferences(clazz.getSourceKey());
        if (refs > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete class: referenced by " + refs + " character sheet(s)");
        }
        if (clazz.getCampaign() != null) {
            referenceCleaner.deletePackageKey(clazz.getCampaign().getId(), CampaignContentType.CLASS, id);
        }
        repository.delete(clazz);
    }
}
