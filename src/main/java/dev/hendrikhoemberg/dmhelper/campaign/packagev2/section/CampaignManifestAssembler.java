package dev.hendrikhoemberg.dmhelper.campaign.packagev2.section;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AdventureDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AssignmentDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AudioCueDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CampaignDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.DiceRollDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.EncounterDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.HandoutDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.LedgerEntryDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.MapDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.NoteDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.PartyMemberDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.QuickNoteDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SessionDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.StatBlockDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.TimelineEventDto;

import java.util.List;

public class CampaignManifestAssembler {

    private CampaignDto campaign;
    private List<AssetDescriptor> assets;
    private List<PartyMemberDto> party;
    private List<StatBlockDto> customStatBlocks;
    private List<CampaignManifestV2.CustomSpellDto> customSpells;
    private List<CampaignManifestV2.CustomConditionDto> customConditions;
    private List<CampaignManifestV2.CustomRuleDto> customRules;
    private List<CampaignManifestV2.CustomEquipmentDto> customEquipment;
    private List<CampaignManifestV2.CustomMagicItemDto> customMagicItems;
    private List<CampaignManifestV2.CustomClassDto> customClasses;
    private List<CampaignManifestV2.CustomSpeciesDto> customSpecies;
    private List<CampaignManifestV2.CustomBackgroundDto> customBackgrounds;
    private List<CampaignManifestV2.CustomFeatDto> customFeats;
    private List<HandoutDto> handouts;
    private List<MapDto> maps;
    private List<EncounterDto> encounters;
    private List<NoteDto> notes;
    private List<QuickNoteDto> quickNotes;
    private List<AssignmentDto> assignments;
    private List<LedgerEntryDto> ledgerEntries;
    private List<TimelineEventDto> timelineEvents;
    private List<AdventureDto> adventures;
    private SessionDto session;
    private List<DiceRollDto> diceRolls;
    private List<CampaignManifestV2.QuestDto> quests;
    private List<CampaignManifestV2.SourceAnnotationDto> annotations;
    private List<CampaignManifestV2.WorldNpcDto> worldNpcs;
    private List<CampaignManifestV2.WorldLocationDto> worldLocations;
    private List<CampaignManifestV2.FactionDto> factions;
    private List<CampaignManifestV2.WorldRelationshipDto> worldRelationships;
    private List<CampaignManifestV2.FactionClockDto> factionClocks;
    private List<CampaignManifestV2.RollableTableDto> rollableTables;
    private List<CampaignManifestV2.TrapDto> traps;
    private List<CampaignManifestV2.HazardDto> hazards;
    private List<AudioCueDto> audioCues;
    private java.util.Set<java.util.UUID> closureStatblockIds;
    private java.util.Set<java.util.UUID> closureMagicItemIds;
    private java.util.Set<java.util.UUID> closureEquipmentIds;
    private java.util.Set<java.util.UUID> closureSpellIds;
    private java.util.Set<java.util.UUID> closureConditionIds;
    private boolean built;

    public void campaign(CampaignDto value) {
        checkNotAlreadySet("campaign", campaign);
        campaign = value;
    }

    public void assets(List<AssetDescriptor> value) {
        checkNotAlreadySet("assets", assets);
        assets = List.copyOf(value);
    }

    public void party(List<PartyMemberDto> value) {
        checkNotAlreadySet("party", party);
        party = List.copyOf(value);
    }

    public void customStatBlocks(List<StatBlockDto> value) {
        checkNotAlreadySet("customStatBlocks", customStatBlocks);
        customStatBlocks = List.copyOf(value);
    }

    public void customSpells(List<CampaignManifestV2.CustomSpellDto> value) {
        checkNotAlreadySet("customSpells", customSpells);
        customSpells = value == null ? List.of() : List.copyOf(value);
    }

    public void customConditions(List<CampaignManifestV2.CustomConditionDto> value) {
        checkNotAlreadySet("customConditions", customConditions);
        customConditions = value == null ? List.of() : List.copyOf(value);
    }

    public void customRules(List<CampaignManifestV2.CustomRuleDto> value) {
        checkNotAlreadySet("customRules", customRules);
        customRules = value == null ? List.of() : List.copyOf(value);
    }

    public void customEquipment(List<CampaignManifestV2.CustomEquipmentDto> value) {
        checkNotAlreadySet("customEquipment", customEquipment);
        customEquipment = value == null ? List.of() : List.copyOf(value);
    }

    public void customMagicItems(List<CampaignManifestV2.CustomMagicItemDto> value) {
        checkNotAlreadySet("customMagicItems", customMagicItems);
        customMagicItems = value == null ? List.of() : List.copyOf(value);
    }

    public void customClasses(List<CampaignManifestV2.CustomClassDto> value) {
        checkNotAlreadySet("customClasses", customClasses);
        customClasses = value == null ? List.of() : List.copyOf(value);
    }

    public void customSpecies(List<CampaignManifestV2.CustomSpeciesDto> value) {
        checkNotAlreadySet("customSpecies", customSpecies);
        customSpecies = value == null ? List.of() : List.copyOf(value);
    }

    public void customBackgrounds(List<CampaignManifestV2.CustomBackgroundDto> value) {
        checkNotAlreadySet("customBackgrounds", customBackgrounds);
        customBackgrounds = value == null ? List.of() : List.copyOf(value);
    }

    public void customFeats(List<CampaignManifestV2.CustomFeatDto> value) {
        checkNotAlreadySet("customFeats", customFeats);
        customFeats = value == null ? List.of() : List.copyOf(value);
    }

    public void handouts(List<HandoutDto> value) {
        checkNotAlreadySet("handouts", handouts);
        handouts = List.copyOf(value);
    }

    public void maps(List<MapDto> value) {
        checkNotAlreadySet("maps", maps);
        maps = List.copyOf(value);
    }

    public void encounters(List<EncounterDto> value) {
        checkNotAlreadySet("encounters", encounters);
        encounters = List.copyOf(value);
    }

    public void notes(List<NoteDto> value) {
        checkNotAlreadySet("notes", notes);
        notes = List.copyOf(value);
    }

    public void quickNotes(List<QuickNoteDto> value) {
        checkNotAlreadySet("quickNotes", quickNotes);
        quickNotes = List.copyOf(value);
    }

    public void assignments(List<AssignmentDto> value) {
        checkNotAlreadySet("assignments", assignments);
        assignments = List.copyOf(value);
    }

    public void ledgerEntries(List<LedgerEntryDto> value) {
        checkNotAlreadySet("ledgerEntries", ledgerEntries);
        ledgerEntries = List.copyOf(value);
    }

    public void timelineEvents(List<TimelineEventDto> value) {
        checkNotAlreadySet("timelineEvents", timelineEvents);
        timelineEvents = List.copyOf(value);
    }

    public void adventures(List<AdventureDto> value) {
        checkNotAlreadySet("adventures", adventures);
        adventures = List.copyOf(value);
    }

    public void session(SessionDto value) {
        checkNotAlreadySet("session", session);
        session = value;
    }

    public void diceRolls(List<DiceRollDto> value) {
        checkNotAlreadySet("diceRolls", diceRolls);
        diceRolls = List.copyOf(value);
    }

    public void quests(List<CampaignManifestV2.QuestDto> value) {
        checkNotAlreadySet("quests", quests);
        quests = value == null ? List.of() : List.copyOf(value);
    }

    public void annotations(List<CampaignManifestV2.SourceAnnotationDto> value) {
        checkNotAlreadySet("annotations", annotations);
        annotations = value == null ? List.of() : List.copyOf(value);
    }

    public void worldNpcs(List<CampaignManifestV2.WorldNpcDto> value) {
        checkNotAlreadySet("worldNpcs", worldNpcs);
        this.worldNpcs = value == null ? List.of() : List.copyOf(value);
    }

    public void worldLocations(List<CampaignManifestV2.WorldLocationDto> value) {
        checkNotAlreadySet("worldLocations", worldLocations);
        this.worldLocations = value == null ? List.of() : List.copyOf(value);
    }

    public void factions(List<CampaignManifestV2.FactionDto> value) {
        checkNotAlreadySet("factions", factions);
        this.factions = value == null ? List.of() : List.copyOf(value);
    }

    public void worldRelationships(List<CampaignManifestV2.WorldRelationshipDto> value) {
        checkNotAlreadySet("worldRelationships", worldRelationships);
        this.worldRelationships = value == null ? List.of() : List.copyOf(value);
    }

    public void factionClocks(List<CampaignManifestV2.FactionClockDto> value) {
        checkNotAlreadySet("factionClocks", factionClocks);
        this.factionClocks = value == null ? List.of() : List.copyOf(value);
    }

    public void rollableTables(List<CampaignManifestV2.RollableTableDto> value) {
        checkNotAlreadySet("rollableTables", rollableTables);
        this.rollableTables = value == null ? List.of() : List.copyOf(value);
    }

    public void traps(List<CampaignManifestV2.TrapDto> value) {
        checkNotAlreadySet("traps", traps);
        this.traps = value == null ? List.of() : List.copyOf(value);
    }

    public void hazards(List<CampaignManifestV2.HazardDto> value) {
        checkNotAlreadySet("hazards", hazards);
        this.hazards = value == null ? List.of() : List.copyOf(value);
    }

    public void audioCues(List<AudioCueDto> value) {
        checkNotAlreadySet("audioCues", audioCues);
        this.audioCues = value == null ? List.of() : List.copyOf(value);
    }

    public java.util.Set<java.util.UUID> closureStatblockIds() { return closureStatblockIds; }
    public java.util.Set<java.util.UUID> closureMagicItemIds() { return closureMagicItemIds; }
    public java.util.Set<java.util.UUID> closureEquipmentIds() { return closureEquipmentIds; }
    public java.util.Set<java.util.UUID> closureSpellIds() { return closureSpellIds; }
    public java.util.Set<java.util.UUID> closureConditionIds() { return closureConditionIds; }

    public void setClosureStatblockIds(java.util.Set<java.util.UUID> ids) {
        if (closureStatblockIds == null) closureStatblockIds = new java.util.HashSet<>();
        closureStatblockIds.addAll(ids);
    }

    public void setClosureMagicItemIds(java.util.Set<java.util.UUID> ids) {
        if (closureMagicItemIds == null) closureMagicItemIds = new java.util.HashSet<>();
        closureMagicItemIds.addAll(ids);
    }

    public void setClosureEquipmentIds(java.util.Set<java.util.UUID> ids) {
        if (closureEquipmentIds == null) closureEquipmentIds = new java.util.HashSet<>();
        closureEquipmentIds.addAll(ids);
    }

    public void setClosureSpellIds(java.util.Set<java.util.UUID> ids) {
        if (closureSpellIds == null) closureSpellIds = new java.util.HashSet<>();
        closureSpellIds.addAll(ids);
    }

    public void setClosureConditionIds(java.util.Set<java.util.UUID> ids) {
        if (closureConditionIds == null) closureConditionIds = new java.util.HashSet<>();
        closureConditionIds.addAll(ids);
    }

    public CampaignManifestV2 build(CampaignManifestV2.Metadata metadata) {
        if (built) {
            throw new IllegalStateException("Manifest already built");
        }
        checkRequired("campaign", campaign);
        checkRequired("assets", assets);
        checkRequired("party", party);
        checkRequired("customStatBlocks", customStatBlocks);
        checkRequired("customSpells", customSpells);
        checkRequired("customConditions", customConditions);
        checkRequired("customRules", customRules);
        checkRequired("customEquipment", customEquipment);
        checkRequired("customMagicItems", customMagicItems);
        checkRequired("customClasses", customClasses);
        checkRequired("customSpecies", customSpecies);
        checkRequired("customBackgrounds", customBackgrounds);
        checkRequired("customFeats", customFeats);
        checkRequired("handouts", handouts);
        checkRequired("maps", maps);
        checkRequired("encounters", encounters);
        checkRequired("notes", notes);
        checkRequired("quickNotes", quickNotes);
        checkRequired("assignments", assignments);
        checkRequired("ledgerEntries", ledgerEntries);
        checkRequired("timelineEvents", timelineEvents);
        checkRequired("adventures", adventures);
        checkRequired("diceRolls", diceRolls);
        checkRequired("audioCues", audioCues);
        built = true;
        return new CampaignManifestV2(
                CampaignManifestV2.CURRENT_FORMAT_VERSION,
                metadata,
                campaign,
                assets,
                party,
                customStatBlocks,
                customSpells == null ? List.of() : customSpells,
                customConditions == null ? List.of() : customConditions,
                customRules == null ? List.of() : customRules,
                customEquipment == null ? List.of() : customEquipment,
                customMagicItems == null ? List.of() : customMagicItems,
                customClasses == null ? List.of() : customClasses,
                customSpecies == null ? List.of() : customSpecies,
                customBackgrounds == null ? List.of() : customBackgrounds,
                customFeats == null ? List.of() : customFeats,
                handouts,
                maps,
                encounters,
                notes,
                quickNotes,
                assignments,
                ledgerEntries,
                timelineEvents,
                adventures,
                session,
                diceRolls,
                quests == null ? List.of() : quests,
                annotations == null ? List.of() : annotations,
                worldNpcs == null ? List.of() : worldNpcs,
                worldLocations == null ? List.of() : worldLocations,
                factions == null ? List.of() : factions,
                worldRelationships == null ? List.of() : worldRelationships,
                factionClocks == null ? List.of() : factionClocks,
                rollableTables == null ? List.of() : rollableTables,
                traps == null ? List.of() : traps,
                hazards == null ? List.of() : hazards,
                audioCues == null ? List.of() : audioCues
        );
    }

    private static void checkNotAlreadySet(String name, Object value) {
        if (value != null) {
            throw new IllegalStateException("Section '" + name + "' already written");
        }
    }

    private static void checkRequired(String name, Object value) {
        if (value == null) {
            throw new IllegalStateException("Required section '" + name + "' was not written");
        }
    }
}
