package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpeciesRepository extends JpaRepository<Species, UUID>,
        JpaSpecificationExecutor<Species> {

    List<Species> findAllByOrderByNameAsc();
}
