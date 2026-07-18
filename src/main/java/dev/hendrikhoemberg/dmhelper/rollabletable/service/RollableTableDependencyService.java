package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReference;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntryReferenceRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RollableTableDependencyService {

    private final RollableTableRepository rollableTableRepository;
    private final RollableTableEntryReferenceRepository referenceRepository;
    private final SceneLinkRepository sceneLinkRepository;
    private final SceneRepository sceneRepository;

    public RollableTableDependencyService(RollableTableRepository rollableTableRepository,
                                           RollableTableEntryReferenceRepository referenceRepository,
                                           SceneLinkRepository sceneLinkRepository,
                                           SceneRepository sceneRepository) {
        this.rollableTableRepository = rollableTableRepository;
        this.referenceRepository = referenceRepository;
        this.sceneLinkRepository = sceneLinkRepository;
        this.sceneRepository = sceneRepository;
    }

    public TableDeletionImpact computeDeletionImpact(UUID tableId) {
        List<TableDependency> deps = new ArrayList<>();

        var sourceTable = rollableTableRepository.findById(tableId);
        String tableName = sourceTable.map(rt -> rt.getName()).orElse("Unknown");

        // References from other table entries
        for (var ref : referenceRepository.findAll()) {
            if (ref.getTargetId() != null && ref.getTargetId().equals(tableId)
                    && "ROLLABLE_TABLE".equals(ref.getTargetType())) {
                var entry = ref.getEntry();
                var entryTable = entry.getTable();
                deps.add(new TableDependency("TABLE_ENTRY", entryTable.getId(),
                        entryTable.getName() + " / " + entry.getEntryKey(),
                        "/entries/" + entryTable.getEntries().indexOf(entry) + "/references/"
                                + entry.getReferences().indexOf(ref)));
            }
        }

        // Scene links referencing this table
        for (var link : sceneLinkRepository.findByTargetId(tableId)) {
            var scene = link.getScene();
            deps.add(new TableDependency("SCENE", scene.getId(),
                    scene.getTitle(), "/links/" + scene.getLinks().indexOf(link)));
        }

        return new TableDeletionImpact(deps);
    }
}
