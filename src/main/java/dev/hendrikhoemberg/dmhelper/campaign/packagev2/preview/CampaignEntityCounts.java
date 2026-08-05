package dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview;

public record CampaignEntityCounts(
        int partyMembers, int customStatBlocks,
        int customSpells, int customConditions, int customRules,
        int customEquipment, int customMagicItems, int customClasses,
        int customSpecies, int customBackgrounds, int customFeats,
        int handouts, int maps, int tokens,
        int encounters, int combatants, int notes, int quickNotes, int assignments,
        int ledgerEntries, int timelineEvents, int adventures, int chapters, int scenes,
        int assets, int combatLogEntries, int noteLinks,
        int sessions, int sessionSceneVisits,
        int traps, int hazards
) {}
