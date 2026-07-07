package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CharacterClassService {

    private final CharacterClassRepository repository;

    public CharacterClassService(CharacterClassRepository repository) {
        this.repository = repository;
    }

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

    public List<CharacterClass> findBaseClasses() {
        return repository.findBySubclassOfIsNullOrderByNameAsc();
    }

    public List<CharacterClass> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public Optional<CharacterClass> findBySourceKey(String sourceKey) {
        return repository.findBySourceKey(sourceKey);
    }
}
