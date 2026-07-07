package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EquipmentItemRepository extends JpaRepository<EquipmentItem, UUID>,
        JpaSpecificationExecutor<EquipmentItem> {

    List<EquipmentItem> findAllByOrderByNameAsc();

    List<EquipmentItem> findByCategoryOrderByNameAsc(EquipmentItem.Category category);
}
