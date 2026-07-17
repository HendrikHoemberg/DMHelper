package dev.hendrikhoemberg.dmhelper.library.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentItemRepository extends JpaRepository<EquipmentItem, UUID>,
        JpaSpecificationExecutor<EquipmentItem> {

    List<EquipmentItem> findAllByOrderByNameAsc();

    List<EquipmentItem> findByCategoryOrderByNameAsc(EquipmentItem.Category category);

    List<EquipmentItem> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    Optional<EquipmentItem> findBySourceKey(String sourceKey);

    Optional<EquipmentItem> findBySourceAndSourceKey(ContentSource source, String sourceKey);

    List<EquipmentItem> findByCampaignIdOrderByNameAsc(UUID campaignId);

    List<EquipmentItem> findBySourceAndCampaignIsNullOrderByNameAsc(ContentSource source);

    boolean existsBySourceAndSourceKeyAndCampaignIsNull(ContentSource source, String sourceKey);

    boolean existsByCampaignIdAndSourceKey(UUID campaignId, String sourceKey);
}
