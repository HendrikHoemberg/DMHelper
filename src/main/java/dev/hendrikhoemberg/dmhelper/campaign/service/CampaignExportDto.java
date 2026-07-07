package dev.hendrikhoemberg.dmhelper.campaign.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record CampaignExportDto(
        int formatVersion,
        CampaignDto campaign,
        List<PartyMemberExportDto> party,
        List<StatBlockExportDto> statBlocks,
        List<Object> handouts,
        List<Object> maps,
        List<Object> encounters,
        List<Object> notes
) {
    public static final int CURRENT_FORMAT_VERSION = 1;

    public static CampaignExportDto from(
            dev.hendrikhoemberg.dmhelper.campaign.data.Campaign campaign,
            List<PartyMemberExportDto> party,
            List<StatBlockExportDto> statBlocks) {
        return new CampaignExportDto(
                CURRENT_FORMAT_VERSION,
                new CampaignDto(campaign.getName(), campaign.getDescription()),
                party,
                statBlocks,
                List.of(), List.of(), List.of(), List.of()
        );
    }

    public static CampaignExportDto from(dev.hendrikhoemberg.dmhelper.campaign.data.Campaign campaign) {
        return from(campaign, List.of(), List.of());
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
            String notes, boolean active
    ) {
        public static PartyMemberExportDto from(
                dev.hendrikhoemberg.dmhelper.party.data.PartyMember pm) {
            return new PartyMemberExportDto(
                    pm.getCharacterName(), pm.getPlayerName(), pm.getClassAndLevel(),
                    pm.getAc(), pm.getMaxHp(), pm.getInitiativeBonus(), pm.getSpeed(),
                    pm.getPassivePerception(), pm.getPassiveInsight(),
                    pm.getPassiveInvestigation(), pm.getNotes(), pm.isActive()
            );
        }
    }

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
}
