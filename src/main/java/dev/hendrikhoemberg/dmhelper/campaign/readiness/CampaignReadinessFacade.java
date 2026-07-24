package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
}
