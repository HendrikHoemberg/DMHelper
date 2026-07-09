package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CharacterClassRepository extends JpaRepository<CharacterClass, UUID>,
        JpaSpecificationExecutor<CharacterClass> {

    List<CharacterClass> findBySubclassOfIsNullOrderByNameAsc();

    List<CharacterClass> findBySubclassOfOrderByNameAsc(String subclassOf);

    List<CharacterClass> findAllByOrderByNameAsc();

    Optional<CharacterClass> findBySourceKey(String sourceKey);

    List<CharacterClass> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
