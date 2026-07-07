package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class EquipmentItemService {

    private final EquipmentItemRepository repository;

    public EquipmentItemService(EquipmentItemRepository repository) {
        this.repository = repository;
    }

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

    public List<EquipmentItem> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public List<EquipmentItem> findByCategory(EquipmentItem.Category category) {
        return repository.findByCategoryOrderByNameAsc(category);
    }
}
