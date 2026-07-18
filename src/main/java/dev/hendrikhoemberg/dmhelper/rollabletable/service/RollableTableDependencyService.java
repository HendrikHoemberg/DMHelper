package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
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

    static final String DEP_KIND_TABLE_ENTRY = "TABLE_ENTRY";
    static final String DEP_KIND_SCENE = "SCENE";
    static final String DEP_KIND_LOCATION = "LOCATION";

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

        // References from other table entries targeting this table
        for (var ref : referenceRepository.findByTargetTypeAndTargetId("ROLLABLE_TABLE", tableId)) {
            var entry = ref.getEntry();
            var entryTable = entry.getTable();
            deps.add(new TableDependency(DEP_KIND_TABLE_ENTRY, entryTable.getId(),
                    entryTable.getName() + " / " + entry.getEntryKey(),
                    "/entries/" + entryTable.getEntries().indexOf(entry) + "/references/"
                            + entry.getReferences().indexOf(ref)));
        }

        // Scene links referencing this table
        for (var link : sceneLinkRepository.findByTargetId(tableId)) {
            var scene = link.getScene();
            deps.add(new TableDependency(DEP_KIND_SCENE, scene.getId(),
                    scene.getTitle(), "/links/" + scene.getLinks().indexOf(link)));
        }

        return new TableDeletionImpact(deps);
    }
}
