package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneEncounterSeedService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class BulkSeedService {

    public record BulkSeedResult(int scenesProcessed, int encountersSeeded,
                                 int combatantsAdded, List<String> skipped) {}

    private final CampaignReadinessFacade readiness;
    private final SceneEncounterSeedService seeder;

    public BulkSeedService(CampaignReadinessFacade readiness,
                           SceneEncounterSeedService seeder) {
        this.readiness = readiness;
        this.seeder = seeder;
    }

    @Transactional
    public BulkSeedResult seedAll(UUID campaignId) {
        List<UUID> sceneIds = readiness.reportForCampaign(campaignId).items().stream()
                .filter(item -> item.repairKind() == ReadinessRepairKind.SEED_ENCOUNTER)
                .map(ReadinessItem::targetId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        int seeded = 0;
        int combatants = 0;
        List<String> skipped = new ArrayList<>();

        for (UUID sceneId : sceneIds) {
            SceneEncounterSeedService.SeedResult result =
                    seeder.seedFromScene(campaignId, sceneId);
            if (!result.alreadyExisted()) seeded++;
            combatants += result.combatantsAdded();
            skipped.addAll(result.skippedParticipants());
        }

        return new BulkSeedResult(sceneIds.size(), seeded, combatants, List.copyOf(skipped));
    }
}
