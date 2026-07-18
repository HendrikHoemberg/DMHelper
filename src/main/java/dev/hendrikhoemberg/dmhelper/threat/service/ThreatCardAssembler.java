package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSection;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.web.ThreatCardView;
import dev.hendrikhoemberg.dmhelper.threat.web.ThreatWebMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds fully-materialized threat card DTOs for scene sections inside a
 * transactional boundary so bag access is safe under open-in-view=false.
 */
@Service
@Transactional(readOnly = true)
public class ThreatCardAssembler {

    private final TrapService trapService;
    private final HazardService hazardService;
    private final MarkdownUtil markdownUtil;

    public ThreatCardAssembler(TrapService trapService,
                               HazardService hazardService,
                               MarkdownUtil markdownUtil) {
        this.trapService = trapService;
        this.hazardService = hazardService;
        this.markdownUtil = markdownUtil;
    }

    public Map<UUID, ThreatCardView> forScene(Scene scene) {
        Map<UUID, ThreatCardView> cards = new HashMap<>();
        if (scene == null || scene.getSections() == null) {
            return cards;
        }
        for (SceneSection section : scene.getSections()) {
            if (section.getThreatKind() == null || section.getThreatId() == null || section.getId() == null) {
                continue;
            }
            ThreatCardView card = switch (section.getThreatKind()) {
                case TRAP -> loadTrapCard(section.getThreatId());
                case HAZARD -> loadHazardCard(section.getThreatId());
            };
            if (card != null) {
                cards.put(section.getId(), card);
            }
        }
        return cards;
    }

    private ThreatCardView loadTrapCard(UUID threatId) {
        try {
            Trap trap = trapService.findDetailedById(threatId);
            return ThreatWebMapper.cardFromTrap(trap, htmlDescription(trap.getDescription()));
        } catch (NotFoundException e) {
            return null;
        }
    }

    private ThreatCardView loadHazardCard(UUID threatId) {
        try {
            Hazard hazard = hazardService.findDetailedById(threatId);
            return ThreatWebMapper.cardFromHazard(hazard, htmlDescription(hazard.getDescription()));
        } catch (NotFoundException e) {
            return null;
        }
    }

    private String htmlDescription(String markdown) {
        return markdown != null ? markdownUtil.toHtml(markdown) : "";
    }
}
