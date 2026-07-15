package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;

import java.util.Map;
import java.util.UUID;

public record CampaignPersistenceReceipt(
        Campaign campaign,
        Map<String, UUID> entityIdsByPointer
) {
    public UUID requireEntityId(String pointer) {
        UUID id = entityIdsByPointer.get(pointer);
        if (id == null) throw new IllegalStateException("No persisted entity for " + pointer);
        return id;
    }
}
