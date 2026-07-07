package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.library.data.RuleSection;
import dev.hendrikhoemberg.dmhelper.library.data.RuleSectionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RuleSectionService {

    private final RuleSectionRepository repository;

    public RuleSectionService(RuleSectionRepository repository) {
        this.repository = repository;
    }

    public List<RuleSection> search(String search, String ruleset) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (ruleset != null && !ruleset.isBlank()) {
                predicates.add(cb.equal(root.get("ruleset"), ruleset));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }
            query.orderBy(cb.asc(root.get("ruleset")), cb.asc(root.get("sortOrder")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    public List<RuleSection> findAll() {
        return repository.findAllByOrderBySortOrderAsc();
    }
}
