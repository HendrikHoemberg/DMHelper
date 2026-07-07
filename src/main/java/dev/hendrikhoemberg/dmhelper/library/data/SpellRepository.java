package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpellRepository extends JpaRepository<Spell, UUID>,
        JpaSpecificationExecutor<Spell> {

    List<Spell> findAllByOrderByLevelAscNameAsc();

    boolean existsBySourceKey(String sourceKey);
}
