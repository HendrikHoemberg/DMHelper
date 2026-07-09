package dev.hendrikhoemberg.dmhelper.campaign.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;

public record CampaignExportDto(
        int formatVersion,
        CampaignDto campaign,
        List<PartyMemberExportDto> party,
        List<StatBlockExportDto> statBlocks,
        List<HandoutExportDto> handouts,
        List<MapExportDto> maps,
        List<EncounterExportDto> encounters,
        List<NoteExportDto> notes,
        List<QuickNoteExportDto> quicknotes,
        List<AssignmentExportDto> assignments,
        List<LedgerExportDto> ledger,
        List<TimelineExportDto> timeline
) {
    public static final int CURRENT_FORMAT_VERSION = 1;

    public static CampaignExportDto from(
            dev.hendrikhoemberg.dmhelper.campaign.data.Campaign campaign,
            List<PartyMemberExportDto> party,
            List<StatBlockExportDto> statBlocks,
            List<MapExportDto> maps) {
        return new CampaignExportDto(
                CURRENT_FORMAT_VERSION,
                new CampaignDto(campaign.getName(), campaign.getDescription()),
                party,
                statBlocks,
                List.of(), maps, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of()
        );
    }

    public static CampaignExportDto from(
            dev.hendrikhoemberg.dmhelper.campaign.data.Campaign campaign,
            List<PartyMemberExportDto> party,
            List<StatBlockExportDto> statBlocks) {
        return from(campaign, party, statBlocks, List.of());
    }

    public static CampaignExportDto from(dev.hendrikhoemberg.dmhelper.campaign.data.Campaign campaign) {
        return from(campaign, List.of(), List.of(), List.of());
    }

    public record CampaignDto(
            String name,
            @JsonInclude(JsonInclude.Include.NON_DEFAULT) String description
    ) {
        public CampaignDto {
            if (description != null && description.isBlank()) {
                description = null;
            }
        }
    }

    public record PartyMemberExportDto(
            String characterName, String playerName, String classAndLevel,
            int ac, int maxHp, int initiativeBonus, int speed,
            int passivePerception, int passiveInsight, int passiveInvestigation,
            String notes, boolean active,
            @JsonInclude(JsonInclude.Include.NON_NULL) SheetExportDto sheet
    ) {
        public static PartyMemberExportDto from(
                dev.hendrikhoemberg.dmhelper.party.data.PartyMember pm) {
            return new PartyMemberExportDto(
                    pm.getCharacterName(), pm.getPlayerName(), pm.getClassAndLevel(),
                    pm.getAc(), pm.getMaxHp(), pm.getInitiativeBonus(), pm.getSpeed(),
                    pm.getPassivePerception(), pm.getPassiveInsight(),
                    pm.getPassiveInvestigation(), pm.getNotes(), pm.isActive(),
                    null
            );
        }
    }

    public record SheetExportDto(
            Map<String, Object> abilityScores,
            List<ClassLevelExportDto> classLevels,
            Map<String, Object> proficiencies,
            String speciesKey,
            String backgroundKey,
            List<String> featRefs,
            int xp,
            Map<String, Object> overrides,
            int hitDiceUsed,
            List<ResourceExportDto> resources,
            List<SpellRefExportDto> spells
    ) {}

    public record ClassLevelExportDto(
            String classSourceKey,
            int level,
            List<Integer> hitDieRolls
    ) {}

    public record ResourceExportDto(
            String name,
            int maxUses,
            int currentUses,
            String resetRule
    ) {}

    public record SpellRefExportDto(
            String spellKey,
            boolean prepared,
            String sourceClass
    ) {}

    public record StatBlockExportDto(
            String sourceKey, String name, String cr, String type,
            String size, String alignment,
            int ac, String hp, String speed,
            int strScore, int dexScore, int conScore, int intScore, int wisScore, int chaScore,
            Integer strSave, Integer dexSave, Integer conSave,
            Integer intSave, Integer wisSave, Integer chaSave,
            String skills,
            String damageVulnerabilities, String damageResistances,
            String damageImmunities, String conditionImmunities,
            String senses, String languages,
            String traits, String actions, String bonusActions, String reactions,
            String legendaryActions, String legendaryDescription, String lairActions,
            int xp
    ) {
        public static StatBlockExportDto from(
                dev.hendrikhoemberg.dmhelper.library.data.StatBlock sb) {
            return new StatBlockExportDto(
                    sb.getSourceKey(), sb.getName(), sb.getCr(), sb.getType(),
                    sb.getSize(), sb.getAlignment(),
                    sb.getAc(), sb.getHp(), sb.getSpeed(),
                    sb.getStrScore(), sb.getDexScore(), sb.getConScore(),
                    sb.getIntScore(), sb.getWisScore(), sb.getChaScore(),
                    sb.getStrSave(), sb.getDexSave(), sb.getConSave(),
                    sb.getIntSave(), sb.getWisSave(), sb.getChaSave(),
                    sb.getSkills(),
                    sb.getDamageVulnerabilities(), sb.getDamageResistances(),
                    sb.getDamageImmunities(), sb.getConditionImmunities(),
                    sb.getSenses(), sb.getLanguages(),
                    sb.getTraits(), sb.getActions(), sb.getBonusActions(), sb.getReactions(),
                    sb.getLegendaryActions(), sb.getLegendaryDescription(), sb.getLairActions(),
                    sb.getXp()
            );
        }
    }

    public record EncounterExportDto(
            String name,
            List<CombatantExportDto> combatants,
            String status,
            int round,
            int activeTurnIndex,
            long logSequence,
            String lairActionName,
            String lairActionDescription
    ) {
        public static EncounterExportDto from(
                dev.hendrikhoemberg.dmhelper.encounter.data.Encounter enc,
                List<CombatantExportDto> combatants) {
            return new EncounterExportDto(
                    enc.getName(), combatants, enc.getStatus().name(),
                    enc.getRound(), enc.getActiveTurnIndex(), enc.getLogSequence(),
                    enc.getLairActionName(), enc.getLairActionDescription());
        }
    }

    public record CombatantExportDto(
            String name, int initiative, int tieBreaker, int sortOrder,
            int maxHp, int currentHp, int tempHp,
            String kind, String groupId, boolean groupLeader,
            String tokenId, String statBlockKey, String partyMemberName,
            boolean defeated, boolean hidden,
            String conditionsJson, String concentratingOn, boolean concentrationCheckPending,
            int legendaryActionsUsed, int legendaryResistancesUsed,
            int legendaryActionsMax, int legendaryResistancesMax,
            String rechargedAbilities, String notes
    ) {
        public static CombatantExportDto from(
                dev.hendrikhoemberg.dmhelper.encounter.data.Combatant c,
                java.util.Map<java.util.UUID, String> tokenIdMap) {
            return new CombatantExportDto(
                    c.getName(), c.getInitiative(), c.getTieBreaker(), c.getSortOrder(),
                    c.getMaxHp(), c.getCurrentHp(), c.getTempHp(),
                    c.getKind(), c.getGroupId(), c.isGroupLeader(),
                    c.getToken() != null ? tokenIdMap.getOrDefault(c.getToken().getId(), c.getToken().getId().toString()) : null,
                    c.getStatBlock() != null ? c.getStatBlock().getSourceKey() : null,
                    c.getPartyMember() != null ? c.getPartyMember().getCharacterName() : null,
                    c.isDefeated(), c.isHidden(),
                    c.getConditionsJson(), c.getConcentratingOn(), c.isConcentrationCheckPending(),
                    c.getLegendaryActionsUsed(), c.getLegendaryResistancesUsed(),
                    c.getLegendaryActionsMax(), c.getLegendaryResistancesMax(),
                    c.getRechargedAbilities(), c.getNotes()
            );
        }
    }

    public record MapExportDto(
            String key,
            String name,
            GridDto grid,
            String movementMode,
            boolean showGrid,
            MapDocumentDto document,
            List<TokenExportDto> tokens
    ) {
        public record GridDto(int w, int h, int cellPx, String gridType) {}

        public record TokenExportDto(String id, String name, String kind, String color,
                                      int positionX, int positionY, int sizeCols, int sizeRows,
                                      boolean hidden, String statBlockKey, String partyMemberName,
                                      Integer currentHp, Integer maxHp, boolean dead, String notes) {}

        public static MapExportDto from(GameMap map, MapDocumentDto document, List<TokenExportDto> tokens) {
            return new MapExportDto(
                    map.getId().toString(),
                    map.getName(),
                    new GridDto(map.getGridWidth(), map.getGridHeight(),
                            map.getCellSizePx(), map.getGridType()),
                    map.getMovementMode(),
                    map.isShowGrid(),
                    document,
                    tokens
            );
        }
    }

    public record NoteExportDto(
            String type,
            String title,
            String body,
            String tags,
            boolean dmOnly
    ) {
        public static NoteExportDto from(dev.hendrikhoemberg.dmhelper.notes.data.Note note) {
            return new NoteExportDto(
                    note.getType().name(),
                    note.getTitle(),
                    note.getBody(),
                    note.getTags(),
                    note.isDmOnly()
            );
        }
    }

    public record QuickNoteExportDto(
            String targetType,
            String targetRef,
            String body,
            String createdAt
    ) {
        public static QuickNoteExportDto from(
                dev.hendrikhoemberg.dmhelper.notes.data.QuickNote qn,
                java.util.Map<java.util.UUID, String> idMappings) {
            String mappedTargetRef = idMappings.getOrDefault(qn.getTargetId(), qn.getTargetId().toString());
            return new QuickNoteExportDto(
                    qn.getTargetType(),
                    mappedTargetRef,
                    qn.getBody(),
                    qn.getCreatedAt().toString()
            );
        }
    }

    public record HandoutExportDto(
            String title,
            List<String> tags,
            String fileName,
            String contentType,
            String imageData
    ) {
        public static HandoutExportDto from(dev.hendrikhoemberg.dmhelper.handout.data.Handout h,
                                             String imageBase64) {
            return new HandoutExportDto(h.getTitle(), parseTags(h.getTags()),
                    h.getFileName(), h.getContentType(), imageBase64);
        }

        private static List<String> parseTags(String tagsStr) {
            if (tagsStr == null || tagsStr.isBlank()) return List.of();
            return Arrays.stream(tagsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    public record AssignmentExportDto(
            UUID id, String holderName,
            String magicItemKey, String equipmentItemKey,
            String customText, int quantity, boolean attuned
    ) {}

    public record LedgerExportDto(
            UUID id, Instant timestamp,
            Integer inGameYear, Integer inGameMonth, Integer inGameDay,
            String kind, String direction,
            BigDecimal amount, String currency,
            String holder, String note
    ) {}

    public record TimelineExportDto(
            UUID id, int inGameYear, int inGameMonth, int inGameDay,
            String title, String body, String noteTitle
    ) {}
}
