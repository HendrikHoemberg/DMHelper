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

@JsonInclude(JsonInclude.Include.NON_NULL)
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
        List<TimelineExportDto> timeline,
        List<AdventureExportDto> adventures
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
                List.of(), List.of(), List.of(), List.of()
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CampaignDto(
            String name,
            String description
    ) {
        public CampaignDto {
            if (description != null && description.isBlank()) {
                description = null;
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
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
            List<SpellRefExportDto> spells,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> spellSlotsUsed
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClassLevelExportDto(
            String classSourceKey,
            int level,
            List<Integer> hitDieRolls
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResourceExportDto(
            String name,
            int maxUses,
            int currentUses,
            String resetRule
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SpellRefExportDto(
            String spellKey,
            boolean prepared,
            String sourceClass
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EncounterExportDto(
            String name,
            List<CombatantExportDto> combatants,
            String status,
            int round,
            int activeTurnIndex,
            long logSequence,
            String lairActionName,
            String lairActionDescription,
            String encounterKey,
            String map
    ) {
        public static EncounterExportDto from(
                dev.hendrikhoemberg.dmhelper.encounter.data.Encounter enc,
                List<CombatantExportDto> combatants) {
            return new EncounterExportDto(
                    enc.getName(), combatants, enc.getStatus().name(),
                    enc.getRound(), enc.getActiveTurnIndex(), enc.getLogSequence(),
                    enc.getLairActionName(), enc.getLairActionDescription(),
                    enc.getEncounterKey(),
                    enc.getMap() != null ? enc.getMap().getName() : null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MapExportDto(
            String key,
            String name,
            GridDto grid,
            String movementMode,
            boolean showGrid,
            MapDocumentDto document,
            List<TokenExportDto> tokens
    ) {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record GridDto(int w, int h, int cellPx, String gridType) {}

        @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AssignmentExportDto(
            UUID id, String holderName,
            String magicItemKey, String equipmentItemKey,
            String customText, int quantity, boolean attuned
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LedgerExportDto(
            UUID id, Instant timestamp,
            Integer inGameYear, Integer inGameMonth, Integer inGameDay,
            String kind, String direction,
            BigDecimal amount, String currency,
            String holder, String note,
            String itemAssignmentRef
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimelineExportDto(
            UUID id, int inGameYear, int inGameMonth, int inGameDay,
            String title, String body, String noteTitle
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdventureExportDto(
            String name,
            String description,
            String sourceAttribution,
            int sortOrder,
            List<ChapterExportDto> chapters
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChapterExportDto(
            String title,
            String intro,
            int sortOrder,
            List<SceneExportDto> scenes
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SceneExportDto(
            String title,
            String sceneKey,
            String body,
            String status,
            int sortOrder,
            String map,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Integer> pin,
            String encounter,
            List<String> statblocks,
            List<String> handouts
    ) {}
}
