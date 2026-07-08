package dev.hendrikhoemberg.dmhelper.notes.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NoteLinkRepository extends JpaRepository<NoteLink, UUID> {

    List<NoteLink> findBySourceNoteId(UUID sourceNoteId);

    List<NoteLink> findByTargetTypeAndTargetId(String targetType, UUID targetId);

    void deleteBySourceNoteId(UUID sourceNoteId);
}
