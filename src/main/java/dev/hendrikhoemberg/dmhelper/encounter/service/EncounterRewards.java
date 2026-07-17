package dev.hendrikhoemberg.dmhelper.encounter.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EncounterRewards(
        Integer xpTotal,
        Integer xpPerPc,
        List<CurrencyGrant> currency,
        List<RewardItem> items,
        List<ContentReference> questObjectiveRefs,
        String notes
) {
    public EncounterRewards {
        if (currency == null) currency = List.of();
        if (items == null) items = List.of();
        if (questObjectiveRefs == null) questObjectiveRefs = List.of();
    }

    public static EncounterRewards empty() {
        return new EncounterRewards(null, null, List.of(), List.of(), List.of(), null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CurrencyGrant(String currency, double amount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RewardItem(
            String customText,
            ContentReference magicItemRef,
            ContentReference equipmentItemRef,
            int quantity
    ) {}
}
