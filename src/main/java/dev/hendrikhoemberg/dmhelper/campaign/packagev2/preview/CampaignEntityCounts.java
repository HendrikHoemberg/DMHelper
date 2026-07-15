package dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview;

import java.time.Instant;

public record CampaignEntityCounts(
        int partyMembers, int customStatBlocks, int handouts, int maps, int tokens,
        int encounters, int combatants, int notes, int quickNotes, int assignments,
        int ledgerEntries, int timelineEvents, int adventures, int chapters, int scenes,
        int assets
) {}
