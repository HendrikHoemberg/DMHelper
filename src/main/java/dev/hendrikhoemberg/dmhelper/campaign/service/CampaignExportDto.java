package dev.hendrikhoemberg.dmhelper.campaign.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;

public record CampaignExportDto(
        int formatVersion,
        CampaignDto campaign,
        List<PartyMemberExportDto> party,
        List<StatBlockExportDto> statBlocks,
        List<HandoutExportDto> handouts,
        List<MapExportDto> maps,
        List<Object> encounters,
        List<NoteExportDto> notes,
        List<QuickNoteExportDto> quicknotes
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
                List.of(), maps, List.of(), List.of(), List.of()
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

    // Encounter export — full serialization in M12
    public record EncounterExportDto(
            String name,
            List<Object> combatants,
            String status,
            int round,
            String lairActionName,
            String lairActionDescription
    ) {
        public static EncounterExportDto from(
                dev.hendrikhoemberg.dmhelper.encounter.data.Encounter enc) {
            return new EncounterExportDto(
                    enc.getName(), List.of(), enc.getStatus().name(),
                    enc.getRound(), enc.getLairActionName(), enc.getLairActionDescription());
        }
    }

    public record MapExportDto(
            String key,
            String name,
            GridDto grid,
            MapDocumentDto document
    ) {
        public record GridDto(int w, int h, int cellPx, String gridType) {}

        public static MapExportDto from(GameMap map, MapDocumentDto document) {
            return new MapExportDto(
                    map.getId().toString(),
                    map.getName(),
                    new GridDto(map.getGridWidth(), map.getGridHeight(),
                            map.getCellSizePx(), map.getGridType()),
                    document
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
            String targetId,
            String body,
            String createdAt
    ) {
        public static QuickNoteExportDto from(
                dev.hendrikhoemberg.dmhelper.notes.data.QuickNote qn,
                java.util.Map<java.util.UUID, String> idMappings) {
            String mappedTargetId = idMappings.getOrDefault(qn.getTargetId(), qn.getTargetId().toString());
            return new QuickNoteExportDto(
                    qn.getTargetType(),
                    mappedTargetId,
                    qn.getBody(),
                    qn.getCreatedAt().toString()
            );
        }
    }

    public record HandoutExportDto(String title, java.util.List<String> tags) {
        public static HandoutExportDto from(dev.hendrikhoemberg.dmhelper.handout.data.Handout h) {
            return new HandoutExportDto(h.getTitle(), java.util.List.of());
        }
    }
}
