package dev.hendrikhoemberg.dmhelper.adventure.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Transactional
public class SceneRefCleaner {

    private final SceneRepository sceneRepository;

    public SceneRefCleaner(SceneRepository sceneRepository) {
        this.sceneRepository = sceneRepository;
    }

    public void detachEncounter(UUID encounterId) {
        for (Scene s : sceneRepository.findByEncounterId(encounterId)) {
            s.setEncounter(null);
            sceneRepository.save(s);
        }
    }

    public void detachMap(UUID mapId) {
        for (Scene s : sceneRepository.findByMapId(mapId)) {
            s.setMap(null);
            s.setPinX(null);
            s.setPinY(null);
            sceneRepository.save(s);
        }
    }

    public void detachStatBlock(UUID statBlockId) {
        for (Scene s : sceneRepository.findAll()) {
            if (s.getStatBlocks().removeIf(sb -> sb.getId().equals(statBlockId))) {
                sceneRepository.save(s);
            }
        }
    }

    public void detachHandout(UUID handoutId) {
        for (Scene s : sceneRepository.findAll()) {
            if (s.getHandouts().removeIf(h -> h.getId().equals(handoutId))) {
                sceneRepository.save(s);
            }
        }
    }
}
