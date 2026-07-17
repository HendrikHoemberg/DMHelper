package dev.hendrikhoemberg.dmhelper.campaign.packagev2.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationConfidence;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapLayerDto;
import dev.hendrikhoemberg.dmhelper.library.data.LicenseClassification;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CampaignManifestV2(
        int formatVersion,
        Metadata metadata,
        CampaignDto campaign,
        List<AssetDescriptor> assets,
        List<PartyMemberDto> party,
        List<StatBlockDto> customStatBlocks,
        List<CustomSpellDto> customSpells,
        List<CustomConditionDto> customConditions,
        List<CustomRuleDto> customRules,
        List<CustomEquipmentDto> customEquipment,
        List<CustomMagicItemDto> customMagicItems,
        List<CustomClassDto> customClasses,
        List<CustomSpeciesDto> customSpecies,
        List<CustomBackgroundDto> customBackgrounds,
        List<CustomFeatDto> customFeats,
        List<HandoutDto> handouts,
        List<MapDto> maps,
        List<EncounterDto> encounters,
        List<NoteDto> notes,
        List<QuickNoteDto> quickNotes,
        List<AssignmentDto> assignments,
        List<LedgerEntryDto> ledgerEntries,
        List<TimelineEventDto> timelineEvents,
        List<AdventureDto> adventures,
        SessionDto session,
        List<DiceRollDto> diceRolls,
        List<QuestDto> quests,
        List<SourceAnnotationDto> annotations
) {
    public static final int CURRENT_FORMAT_VERSION = 2;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Metadata(
            String packageKey,
            Instant createdAt,
            String generator,
            String catalogVersion,
            String catalogSha256,
            List<CampaignExportExclusion> exclusions
    ) {}

    public enum LevelingMode { XP, MILESTONE }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CampaignDto(
            String key,
            String name,
            String description,
            Instant createdAt,
            CampaignSettingsDto settings,
            ContentReference currentSceneRef
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CampaignSettingsDto(
            LevelingMode levelingMode,
            CalendarConfigDto calendar,
            InGameDateDto currentDate
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CalendarConfigDto(
            List<Integer> monthLengths,
            List<String> monthNames,
            List<String> weekdayNames
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InGameDateDto(int year, int month, int day) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PartyMemberDto(
            String key,
            String characterName,
            String playerName,
            String classAndLevel,
            int ac,
            int maxHp,
            int currentHp,
            int initiativeBonus,
            int speed,
            int passivePerception,
            int passiveInsight,
            int passiveInvestigation,
            String notes,
            boolean active,
            SheetDto sheet
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SheetDto(
            String key,
            Map<String, Object> abilityScores,
            List<ClassLevelDto> classLevels,
            Map<String, Object> proficiencies,
            ContentReference speciesRef,
            ContentReference backgroundRef,
            List<ContentReference> featRefs,
            int xp,
            Map<String, Object> overrides,
            int hitDiceUsed,
            List<ResourceDto> resources,
            List<SpellRefDto> spells,
            Map<String, Object> spellSlotsUsed
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClassLevelDto(
            ContentReference classRef,
            int level,
            List<Integer> hitDieRolls
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResourceDto(
            String key,
            String name,
            int maxUses,
            int currentUses,
            String resetRule
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SpellRefDto(
            ContentReference spellRef,
            boolean prepared,
            ContentReference sourceClassRef
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StatBlockDto(
            String key,
            String sourceKey,
            String name,
            String cr,
            String type,
            String size,
            String alignment,
            int ac,
            String hp,
            String speed,
            int strScore,
            int dexScore,
            int conScore,
            int intScore,
            int wisScore,
            int chaScore,
            Integer strSave,
            Integer dexSave,
            Integer conSave,
            Integer intSave,
            Integer wisSave,
            Integer chaSave,
            String skills,
            String damageVulnerabilities,
            String damageResistances,
            String damageImmunities,
            String conditionImmunities,
            String senses,
            String languages,
            String traits,
            String actions,
            String bonusActions,
            String reactions,
            String legendaryActions,
            String legendaryDescription,
            String lairActions,
            int xp,
            Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HandoutDto(
            String key,
            String title,
            List<String> tags,
            String assetRef,
            String contentType,
            boolean dmOnly,
            boolean presented
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MapDto(
            String key,
            String name,
            GridDto grid,
            String movementMode,
            boolean showGrid,
            MapDocumentV2 document,
            List<TokenDto> tokens,
            int sortOrder
    ) {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record GridDto(int w, int h, int cellPx, String gridType) {}

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record MapDocumentV2(
                int schemaVersion,
                MapDocumentDto.GridDto grid,
                List<LayerDto> layers,
                List<MapDocumentDto.PrimitiveDto> primitives,
                List<MapDocumentDto.TerrainDefDto> customTerrain
        ) {}

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record LayerDto(
                String id,
                String name,
                MapLayerDto.LayerType type,
                Boolean visible,
                Boolean locked,
                List<MapLayerDto.CellDto> cells,
                List<MapLayerDto.ShapeDto> shapes,
                ImageDto image
        ) {}

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ImageDto(
                String assetRef,
                double x,
                double y,
                double width,
                double height
        ) {}

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record TokenDto(
                String key,
                String name,
                String kind,
                String color,
                int positionX,
                int positionY,
                int sizeCols,
                int sizeRows,
                boolean hidden,
                ContentReference statBlockRef,
                ContentReference partyMemberRef,
                Integer currentHp,
                Integer maxHp,
                boolean dead,
                String notes,
                String icon
        ) {}
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EncounterDto(
            String key,
            String name,
            List<CombatantDto> combatants,
            String status,
            int round,
            int activeTurnIndex,
            long logSequence,
            String lairActionName,
            String lairActionDescription,
            ContentReference mapRef,
            boolean lairActionTriggered,
            List<CombatLogEntryDto> combatLog
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CombatantDto(
            String key,
            String name,
            int initiative,
            int tieBreaker,
            int sortOrder,
            int maxHp,
            int currentHp,
            int tempHp,
            String kind,
            String groupId,
            boolean groupLeader,
            ContentReference tokenRef,
            ContentReference statBlockRef,
            ContentReference partyMemberRef,
            boolean defeated,
            boolean hidden,
            String conditionsJson,
            String concentratingOn,
            boolean concentrationCheckPending,
            int legendaryActionsUsed,
            int legendaryResistancesUsed,
            int legendaryActionsMax,
            int legendaryResistancesMax,
            String rechargedAbilities,
            String notes
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NoteDto(
            String key,
            String type,
            String title,
            String body,
            String tags,
            boolean dmOnly,
            Instant createdAt,
            List<NoteLinkDto> links
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuickNoteDto(
            String key,
            ContentReference targetRef,
            String body,
            Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AssignmentDto(
            String key,
            ContentReference holderRef,
            ContentReference magicItemRef,
            ContentReference equipmentItemRef,
            String customText,
            int quantity,
            boolean attuned
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LedgerEntryDto(
            String key,
            Instant timestamp,
            Integer inGameYear,
            Integer inGameMonth,
            Integer inGameDay,
            String kind,
            String direction,
            BigDecimal amount,
            String currency,
            String holder,
            String note,
            ContentReference itemAssignmentRef
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimelineEventDto(
            String key,
            int inGameYear,
            int inGameMonth,
            int inGameDay,
            String title,
            String body,
            ContentReference noteRef
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdventureDto(
            String key,
            String name,
            String description,
            String sourceAttribution,
            int sortOrder,
            List<ChapterDto> chapters,
            Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChapterDto(
            String key,
            String title,
            String intro,
            int sortOrder,
            List<SceneDto> scenes
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SceneDto(
            String key,
            String title,
            String body,
            String status,
            int sortOrder,
            ContentReference mapRef,
            Map<String, Integer> pin,
            ContentReference encounterRef,
            List<ContentReference> statblockRefs,
            List<ContentReference> handoutRefs,
            String summary,
            String sourceLocator,
            List<String> tags,
            String mapRegionKey,
            List<SceneSectionDto> sections,
            List<SceneCheckDto> checks,
            List<SceneParticipantDto> participants,
            List<SceneTransitionDto> transitions,
            List<SceneLinkDto> links
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SceneSectionDto(
            String kind,
            String label,
            String body,
            String sourceLocator,
            int sortOrder
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SceneCheckDto(
            String label,
            String ability,
            String skill,
            Integer dc,
            String visibility,
            String success,
            String failure,
            String partial,
            ContentReference ruleRef,
            String sourceLocator,
            int sortOrder
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SceneParticipantDto(
            String displayName,
            int quantity,
            String disposition,
            String placementHint,
            ContentReference statblockRef,
            ContentReference noteRef,
            String sourceLocator,
            int sortOrder
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SceneTransitionDto(
            String key,
            String kind,
            String label,
            ContentReference targetSceneRef,
            String externalDestination,
            String condition,
            String dmNote,
            String sourceLocator,
            int sortOrder
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SceneLinkDto(
            String role,
            ContentReference targetRef,
            String displayText,
            String condition,
            int sortOrder
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionDto(
            String key,
            String status,
            Instant startedAt,
            Instant pausedAt,
            Instant reviewStartedAt,
            InGameDateDto startInGameDate,
            ContentReference planNoteRef,
            ContentReference workspaceMapRef,
            String presentationMode,
            ContentReference presentedRef,
            List<ContentReference> attendeeRefs,
            List<SessionSceneVisitDto> sceneVisits,
            String draftBody,
            List<SessionObjectiveChangeDto> objectiveChanges) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionSceneVisitDto(
            String key,
            ContentReference sceneRef,
            Instant visitedAt,
            Instant completedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CombatLogEntryDto(
            String key,
            int round,
            long sequence,
            String type,
            ContentReference combatantRef,
            JsonNode payload,
            Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DiceRollDto(
            String key,
            String expression,
            List<DiceResult.DieRoll> rolls,
            int modifier,
            int total,
            boolean advantage,
            boolean disadvantage,
            ContentReference encounterRef,
            Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionObjectiveChangeDto(
            String key,
            ContentReference objectiveRef,
            String previousStatus,
            String newStatus,
            Instant changedAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestDto(
            String key,
            String title,
            String status,
            String summary,
            String sourceLocator,
            List<String> tags,
            String rewards,
            String prerequisites,
            String outcomeNotes,
            List<QuestLinkDto> links,
            List<QuestObjectiveDto> objectives,
            Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestLinkDto(
            String role,
            ContentReference targetRef,
            String displayText,
            String condition,
            int sortOrder
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestObjectiveDto(
            String key,
            String title,
            String description,
            String status,
            String completionMode,
            int sortOrder,
            List<ContentReference> prerequisiteRefs,
            String sourceLocator
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceAnnotationDto(
            String key,
            ContentReference ownerRef,
            String fieldPath,
            String message,
            String confidence,
            String sourceLocator,
            String status,
            String resolutionNote,
            Instant createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NoteLinkDto(
            String targetType,
            ContentReference targetRef,
            String displayText,
            boolean resolved
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProvenanceDto(
            String sourceTitle,
            String editionVersion,
            String sourceLocator,
            LicenseClassification licenseClassification,
            Instant importedAt,
            String converterId,
            String converterVersion,
            String sourceHash,
            SourceAnnotationConfidence extractionConfidence
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomSpellDto(
            String key,
            String sourceKey,
            String name,
            int level,
            String school,
            String castingTime,
            String range,
            String components,
            String duration,
            String description,
            String higherLevel,
            boolean ritual,
            boolean concentration,
            ProvenanceDto provenance
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomConditionDto(
            String key,
            String sourceKey,
            String name,
            String description,
            ProvenanceDto provenance
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomRuleDto(
            String key,
            String sourceKey,
            String name,
            String body,
            String parentKey,
            int sortOrder,
            String ruleset,
            ProvenanceDto provenance
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomEquipmentDto(
            String key,
            String sourceKey,
            String name,
            String category,
            String cost,
            String weight,
            String properties,
            String description,
            ProvenanceDto provenance
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomMagicItemDto(
            String key,
            String sourceKey,
            String name,
            String rarity,
            String category,
            String type,
            String description,
            String weight,
            String cost,
            boolean requiresAttunement,
            String attunementDetail,
            ProvenanceDto provenance
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomClassDto(
            String key,
            String sourceKey,
            String name,
            String hitDie,
            String subclassOf,
            String description,
            String savingThrows,
            String features,
            String spellcasting,
            String proficiencies,
            ProvenanceDto provenance
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomSpeciesDto(
            String key,
            String sourceKey,
            String name,
            String size,
            String speed,
            String traits,
            String description,
            ProvenanceDto provenance
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomBackgroundDto(
            String key,
            String sourceKey,
            String name,
            String abilityScores,
            String featRef,
            String skills,
            String tools,
            String description,
            String equipment,
            ProvenanceDto provenance
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CustomFeatDto(
            String key,
            String sourceKey,
            String name,
            String category,
            String prerequisite,
            String benefit,
            ProvenanceDto provenance
    ) {}
}
