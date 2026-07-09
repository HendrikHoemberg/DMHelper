package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeatRepository extends JpaRepository<Feat, UUID>,
        JpaSpecificationExecutor<Feat> {

    List<Feat> findAllByOrderByNameAsc();

    List<Feat> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
