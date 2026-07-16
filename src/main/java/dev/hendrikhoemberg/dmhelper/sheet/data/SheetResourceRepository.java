package dev.hendrikhoemberg.dmhelper.sheet.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SheetResourceRepository extends JpaRepository<SheetResource, UUID> {
    List<SheetResource> findBySheetId(UUID sheetId);
    void deleteBySheetId(UUID sheetId);

    List<SheetResource> findBySheetIdOrderByIdAsc(UUID sheetId);
}
