package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SpellService {

    private final SpellRepository repository;
    private final CampaignRepository campaignRepository;
    private final CustomContentSupport customContentSupport;
    private final LibraryReferenceCleaner referenceCleaner;

    public SpellService(SpellRepository repository,
                        CampaignRepository campaignRepository,
                        CustomContentSupport customContentSupport,
                        LibraryReferenceCleaner referenceCleaner) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.customContentSupport = customContentSupport;
        this.referenceCleaner = referenceCleaner;
    }

    public record SpellWrite(
        String name, int level, String school, String castingTime, String range,
        String components, String duration, String description, String higherLevel,
        boolean ritual, boolean concentration, String sourceKey
    ) {}

    public List<Spell> search(String search, Integer level, String school) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (level != null) {
                predicates.add(cb.equal(root.get("level"), level));
            }
            if (school != null && !school.isBlank()) {
                predicates.add(cb.equal(root.get("school"), school));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("level")), cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<Spell> findAll() {
        return repository.findAllByOrderByLevelAscNameAsc();
    }

    public List<Spell> search(ContentSource source, UUID campaignId, String text) {
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
            query.orderBy(cb.asc(root.get("level")), cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public Spell findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Spell not found: " + id));
    }

    public List<Spell> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional
    public Spell createCustom(UUID campaignIdOrNull, SpellWrite request, ContentProvenance provenanceOrNull) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        Spell spell = new Spell();
        spell.setSource(ContentSource.CUSTOM);
        if (campaignIdOrNull != null) {
            campaignRepository.findById(campaignIdOrNull).ifPresent(spell::setCampaign);
        }
        spell.setName(request.name());
        spell.setLevel(request.level());
        spell.setSchool(request.school());
        spell.setCastingTime(request.castingTime());
        spell.setRange(request.range());
        spell.setComponents(request.components());
        spell.setDuration(request.duration());
        spell.setDescription(request.description());
        spell.setHigherLevel(request.higherLevel());
        spell.setRitual(request.ritual());
        spell.setConcentration(request.concentration());
        if (request.sourceKey() != null && !request.sourceKey().isBlank()) {
            spell.setSourceKey(request.sourceKey());
        } else {
            spell.setSourceKey(customContentSupport.slugify(request.name()));
        }
        if (provenanceOrNull != null) {
            spell.setProvenance(provenanceOrNull);
        }
        return repository.save(spell);
    }

    @Transactional
    public Spell updateCustom(UUID id, SpellWrite request, ContentProvenance provenanceOrNull) {
        Spell spell = findById(id);
        customContentSupport.assertCustom(spell.getSource());
        spell.setName(request.name());
        spell.setLevel(request.level());
        if (request.school() != null) spell.setSchool(request.school());
        if (request.castingTime() != null) spell.setCastingTime(request.castingTime());
        if (request.range() != null) spell.setRange(request.range());
        if (request.components() != null) spell.setComponents(request.components());
        if (request.duration() != null) spell.setDuration(request.duration());
        if (request.description() != null) spell.setDescription(request.description());
        if (request.higherLevel() != null) spell.setHigherLevel(request.higherLevel());
        spell.setRitual(request.ritual());
        spell.setConcentration(request.concentration());
        if (provenanceOrNull != null) {
            spell.setProvenance(provenanceOrNull);
        }
        return repository.save(spell);
    }

    @Transactional
    public Spell cloneAsCustom(UUID sourceId, UUID targetCampaignIdOrNull, String newName) {
        Spell original = findById(sourceId);
        Spell clone = new Spell();
        clone.setSource(ContentSource.CUSTOM);
        if (targetCampaignIdOrNull != null) {
            campaignRepository.findById(targetCampaignIdOrNull).ifPresent(clone::setCampaign);
        }
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setSourceKey(customContentSupport.slugify(clone.getName()));
        clone.setLevel(original.getLevel());
        clone.setSchool(original.getSchool());
        clone.setCastingTime(original.getCastingTime());
        clone.setRange(original.getRange());
        clone.setComponents(original.getComponents());
        clone.setDuration(original.getDuration());
        clone.setDescription(original.getDescription());
        clone.setHigherLevel(original.getHigherLevel());
        clone.setRitual(original.isRitual());
        clone.setConcentration(original.isConcentration());
        if (original.getProvenance() != null) {
            clone.setProvenance(original.getProvenance());
        } else {
            clone.setProvenance(customContentSupport.defaultForSrdClone());
        }
        return repository.save(clone);
    }

    @Transactional
    public Spell promoteToGlobal(UUID id) {
        Spell spell = findById(id);
        customContentSupport.assertCustom(spell.getSource());
        if (spell.getCampaign() != null) {
            UUID oldCampaignId = spell.getCampaign().getId();
            spell.setCampaign(null);
            referenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.SPELL, id);
        }
        return repository.save(spell);
    }

    @Transactional
    public void deleteCustom(UUID id) {
        Spell spell = findById(id);
        customContentSupport.assertCustom(spell.getSource());
        int refs = referenceCleaner.countSpellReferences(id);
        if (refs > 0) {
            throw new IllegalArgumentException(
                "Cannot delete spell: referenced by " + refs + " character sheet(s)");
        }
        if (spell.getCampaign() != null) {
            referenceCleaner.deletePackageKey(spell.getCampaign().getId(), CampaignContentType.SPELL, id);
        }
        repository.delete(spell);
    }
}
