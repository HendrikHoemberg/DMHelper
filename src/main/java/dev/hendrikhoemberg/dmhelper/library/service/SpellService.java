package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SpellService {

    private final SpellRepository repository;

    public SpellService(SpellRepository repository) {
        this.repository = repository;
    }

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
}
