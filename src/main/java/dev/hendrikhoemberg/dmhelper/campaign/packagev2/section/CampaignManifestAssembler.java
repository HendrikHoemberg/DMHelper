package dev.hendrikhoemberg.dmhelper.campaign.packagev2.section;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AdventureDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AssignmentDto;
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

    public CampaignManifestV2 build(CampaignManifestV2.Metadata metadata) {
        if (built) {
            throw new IllegalStateException("Manifest already built");
        }
        checkRequired("campaign", campaign);
        checkRequired("assets", assets);
        checkRequired("party", party);
        checkRequired("customStatBlocks", customStatBlocks);
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
        built = true;
        return new CampaignManifestV2(
                CampaignManifestV2.CURRENT_FORMAT_VERSION,
                metadata,
                campaign,
                assets,
                party,
                customStatBlocks,
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
                diceRolls
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
