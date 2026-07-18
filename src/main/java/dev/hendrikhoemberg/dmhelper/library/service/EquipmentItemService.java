package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EquipmentItemService {

    private final EquipmentItemRepository repository;
    private final CampaignRepository campaignRepository;
    private final CustomContentSupport customContentSupport;
    private final LibraryReferenceCleaner referenceCleaner;

    public EquipmentItemService(EquipmentItemRepository repository,
                                CampaignRepository campaignRepository,
                                CustomContentSupport customContentSupport,
                                LibraryReferenceCleaner referenceCleaner) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.customContentSupport = customContentSupport;
        this.referenceCleaner = referenceCleaner;
    }

    public record EquipmentItemWrite(
        String name, EquipmentItem.Category category, String cost, String weight,
        String properties, String description, String sourceKey
    ) {}

    public List<EquipmentItem> search(String search, EquipmentItem.Category category) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (category != null) {
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

    public List<EquipmentItem> search(ContentSource source, UUID campaignId, String text) {
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

    public List<EquipmentItem> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public List<EquipmentItem> findByCategory(EquipmentItem.Category category) {
        return repository.findByCategoryOrderByNameAsc(category);
    }

    public EquipmentItem findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EquipmentItem not found: " + id));
    }

    public List<EquipmentItem> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional
    public EquipmentItem createCustom(UUID campaignIdOrNull, EquipmentItemWrite request, ContentProvenance provenanceOrNull) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        EquipmentItem item = new EquipmentItem();
        item.setSource(ContentSource.CUSTOM);
        if (campaignIdOrNull != null) {
            campaignRepository.findById(campaignIdOrNull).ifPresent(item::setCampaign);
        }
        item.setName(request.name());
        item.setCategory(request.category());
        if (request.cost() != null) item.setCost(request.cost());
        if (request.weight() != null) item.setWeight(request.weight());
        if (request.properties() != null) item.setProperties(request.properties());
        if (request.description() != null) item.setDescription(request.description());
        if (request.sourceKey() != null && !request.sourceKey().isBlank()) {
            item.setSourceKey(request.sourceKey());
        } else {
            item.setSourceKey(customContentSupport.slugify(request.name()));
        }
        if (provenanceOrNull != null) {
            item.setProvenance(provenanceOrNull);
        }
        return repository.save(item);
    }

    @Transactional
    public EquipmentItem updateCustom(UUID id, EquipmentItemWrite request, ContentProvenance provenanceOrNull) {
        EquipmentItem item = findById(id);
        customContentSupport.assertCustom(item.getSource());
        item.setName(request.name());
        if (request.category() != null) item.setCategory(request.category());
        if (request.cost() != null) item.setCost(request.cost());
        if (request.weight() != null) item.setWeight(request.weight());
        if (request.properties() != null) item.setProperties(request.properties());
        if (request.description() != null) item.setDescription(request.description());
        if (provenanceOrNull != null) {
            item.setProvenance(provenanceOrNull);
        }
        return repository.save(item);
    }

    @Transactional
    public EquipmentItem cloneAsCustom(UUID sourceId, UUID targetCampaignIdOrNull, String newName) {
        EquipmentItem original = findById(sourceId);
        EquipmentItem clone = new EquipmentItem();
        clone.setSource(ContentSource.CUSTOM);
        if (targetCampaignIdOrNull != null) {
            campaignRepository.findById(targetCampaignIdOrNull).ifPresent(clone::setCampaign);
        }
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setSourceKey(customContentSupport.slugify(clone.getName()));
        clone.setCategory(original.getCategory());
        clone.setCost(original.getCost());
        clone.setWeight(original.getWeight());
        clone.setProperties(original.getProperties());
        clone.setDescription(original.getDescription());
        if (original.getProvenance() != null) {
            clone.setProvenance(original.getProvenance());
        } else {
            clone.setProvenance(customContentSupport.defaultForSrdClone());
        }
        return repository.save(clone);
    }

    @Transactional
    public EquipmentItem promoteToGlobal(UUID id) {
        EquipmentItem item = findById(id);
        customContentSupport.assertCustom(item.getSource());
        if (item.getCampaign() != null) {
            UUID oldCampaignId = item.getCampaign().getId();
            item.setCampaign(null);
            referenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.EQUIPMENT_ITEM, id);
        }
        return repository.save(item);
    }

    @Transactional
    public void deleteCustom(UUID id) {
        EquipmentItem item = findById(id);
        customContentSupport.assertCustom(item.getSource());
        int treasuryRefs = referenceCleaner.countEquipmentItemReferences(id);
        int threatRefs = referenceCleaner.countThreatReferences(CampaignContentType.EQUIPMENT_ITEM, id);
        int refs = treasuryRefs + threatRefs;
        if (refs > 0) {
            throw new IllegalArgumentException(
                "Cannot delete equipment item: referenced by " + refs + " dependent(s)");
        }
        if (item.getCampaign() != null) {
            referenceCleaner.deletePackageKey(item.getCampaign().getId(), CampaignContentType.EQUIPMENT_ITEM, id);
        }
        repository.delete(item);
    }
}
