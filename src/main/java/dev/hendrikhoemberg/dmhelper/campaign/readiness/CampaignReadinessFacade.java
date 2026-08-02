package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CampaignReadinessFacade {

    private final ReadinessInputsAssembler assembler;
    private final CampaignReadinessService service;
    private final ReadinessAcknowledgementRepository acknowledgements;

    public CampaignReadinessFacade(ReadinessInputsAssembler assembler,
                                   CampaignReadinessService service,
                                   ReadinessAcknowledgementRepository acknowledgements) {
        this.assembler = assembler;
        this.service = service;
        this.acknowledgements = acknowledgements;
    }

    @Transactional(readOnly = true)
    public CampaignReadinessReport reportForCampaign(UUID campaignId) {
        Set<String> accepted = acknowledgements.findByCampaignId(campaignId).stream()
                .map(ReadinessAcknowledgement::getItemKey)
                .collect(Collectors.toSet());
        return service.compute(assembler.fromCampaign(campaignId), accepted);
    }

    /**
     * Readiness for a whole campaign index in a fixed number of queries. Spec 11.1 asks the
     * campaign card to show readiness, and doing that a card at a time is an N+1 — the index
     * is unbounded. Same {@link CampaignReadinessService#compute} call as the single-campaign
     * path above; only the input assembly is batched, so the two cannot disagree about what
     * "ready" means. {@code CampaignReadinessBatchTest} holds them to that.
     */
    @Transactional(readOnly = true)
    public Map<UUID, CampaignReadinessReport> reportsForCampaigns(Collection<UUID> campaignIds) {
        if (campaignIds.isEmpty()) return Map.of();

        Map<UUID, Set<String>> acceptedByCampaign = acknowledgements.findByCampaignIdIn(campaignIds)
                .stream()
                .collect(Collectors.groupingBy(ReadinessAcknowledgement::getCampaignId,
                        Collectors.mapping(ReadinessAcknowledgement::getItemKey,
                                Collectors.toSet())));

        Map<UUID, ReadinessInputs> inputs = assembler.fromCampaigns(campaignIds);
        Map<UUID, CampaignReadinessReport> reports = new HashMap<>();
        inputs.forEach((campaignId, campaignInputs) -> reports.put(campaignId,
                service.compute(campaignInputs,
                        acceptedByCampaign.getOrDefault(campaignId, Set.of()))));
        return reports;
    }
}
