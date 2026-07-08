package dev.hendrikhoemberg.dmhelper.sheet.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SheetSpellReferenceRepository extends JpaRepository<SheetSpellReference, UUID> {
    List<SheetSpellReference> findBySheetId(UUID sheetId);
    List<SheetSpellReference> findBySheetIdAndSourceClass(UUID sheetId, String sourceClass);
    void deleteBySheetId(UUID sheetId);
}
