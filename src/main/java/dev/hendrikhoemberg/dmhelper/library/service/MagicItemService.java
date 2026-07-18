package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MagicItemService {

    private final MagicItemRepository repository;
    private final CampaignRepository campaignRepository;
    private final CustomContentSupport customContentSupport;
    private final LibraryReferenceCleaner referenceCleaner;

    public MagicItemService(MagicItemRepository repository,
                            CampaignRepository campaignRepository,
                            CustomContentSupport customContentSupport,
                            LibraryReferenceCleaner referenceCleaner) {
        this.repository = repository;
        this.campaignRepository = campaignRepository;
        this.customContentSupport = customContentSupport;
        this.referenceCleaner = referenceCleaner;
    }

    public record MagicItemWrite(
        String name, String rarity, String category, String type, String description,
        String weight, String cost, boolean requiresAttunement, String attunementDetail,
        String sourceKey
    ) {}

    public List<MagicItem> search(String search, String rarity, String category) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (rarity != null && !rarity.isBlank()) {
                predicates.add(cb.equal(root.get("rarity"), rarity));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<MagicItem> search(ContentSource source, UUID campaignId, String text) {
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

    public List<MagicItem> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public MagicItem findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MagicItem not found: " + id));
    }

    public List<MagicItem> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional
    public MagicItem createCustom(UUID campaignIdOrNull, MagicItemWrite request, ContentProvenance provenanceOrNull) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        MagicItem item = new MagicItem();
        item.setSource(ContentSource.CUSTOM);
        if (campaignIdOrNull != null) {
            campaignRepository.findById(campaignIdOrNull).ifPresent(item::setCampaign);
        }
        item.setName(request.name());
        if (request.rarity() != null) item.setRarity(request.rarity());
        if (request.category() != null) item.setCategory(request.category());
        if (request.type() != null) item.setType(request.type());
        if (request.description() != null) item.setDescription(request.description());
        if (request.weight() != null) item.setWeight(request.weight());
        if (request.cost() != null) item.setCost(request.cost());
        item.setRequiresAttunement(request.requiresAttunement());
        if (request.attunementDetail() != null) item.setAttunementDetail(request.attunementDetail());
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
    public MagicItem updateCustom(UUID id, MagicItemWrite request, ContentProvenance provenanceOrNull) {
        MagicItem item = findById(id);
        customContentSupport.assertCustom(item.getSource());
        item.setName(request.name());
        if (request.rarity() != null) item.setRarity(request.rarity());
        if (request.category() != null) item.setCategory(request.category());
        if (request.type() != null) item.setType(request.type());
        if (request.description() != null) item.setDescription(request.description());
        if (request.weight() != null) item.setWeight(request.weight());
        if (request.cost() != null) item.setCost(request.cost());
        item.setRequiresAttunement(request.requiresAttunement());
        if (request.attunementDetail() != null) item.setAttunementDetail(request.attunementDetail());
        if (provenanceOrNull != null) {
            item.setProvenance(provenanceOrNull);
        }
        return repository.save(item);
    }

    @Transactional
    public MagicItem cloneAsCustom(UUID sourceId, UUID targetCampaignIdOrNull, String newName) {
        MagicItem original = findById(sourceId);
        MagicItem clone = new MagicItem();
        clone.setSource(ContentSource.CUSTOM);
        if (targetCampaignIdOrNull != null) {
            campaignRepository.findById(targetCampaignIdOrNull).ifPresent(clone::setCampaign);
        }
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setSourceKey(customContentSupport.slugify(clone.getName()));
        clone.setRarity(original.getRarity());
        clone.setCategory(original.getCategory());
        clone.setType(original.getType());
        clone.setDescription(original.getDescription());
        clone.setWeight(original.getWeight());
        clone.setCost(original.getCost());
        clone.setRequiresAttunement(original.isRequiresAttunement());
        clone.setAttunementDetail(original.getAttunementDetail());
        if (original.getProvenance() != null) {
            clone.setProvenance(original.getProvenance());
        } else {
            clone.setProvenance(customContentSupport.defaultForSrdClone());
        }
        return repository.save(clone);
    }

    @Transactional
    public MagicItem promoteToGlobal(UUID id) {
        MagicItem item = findById(id);
        customContentSupport.assertCustom(item.getSource());
        if (item.getCampaign() != null) {
            UUID oldCampaignId = item.getCampaign().getId();
            item.setCampaign(null);
            referenceCleaner.deletePackageKey(oldCampaignId, CampaignContentType.MAGIC_ITEM, id);
        }
        return repository.save(item);
    }

    @Transactional
    public void deleteCustom(UUID id) {
        MagicItem item = findById(id);
        customContentSupport.assertCustom(item.getSource());
        int treasuryRefs = referenceCleaner.countMagicItemReferences(id);
        int threatRefs = referenceCleaner.countThreatReferences(CampaignContentType.MAGIC_ITEM, id);
        int refs = treasuryRefs + threatRefs;
        if (refs > 0) {
            throw new IllegalArgumentException(
                "Cannot delete magic item: referenced by " + refs + " dependent(s)");
        }
        if (item.getCampaign() != null) {
            referenceCleaner.deletePackageKey(item.getCampaign().getId(), CampaignContentType.MAGIC_ITEM, id);
        }
        repository.delete(item);
    }
}
