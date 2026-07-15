package dev.hendrikhoemberg.dmhelper.campaign.packagev2.migration;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.AssetSignatureValidator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.PackageKeyGenerator;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignFormatMigration;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignExportDto;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

@Component
public class LegacyV1ToV2Migration implements CampaignFormatMigration {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    static final List<String> EXCLUSIONS = List.of(
            "CAMPAIGN_SETTINGS", "CURRENT_SCENE", "PARTY_CURRENT_HP",
            "HANDOUT_PRESENTATION_STATE", "COMBAT_LOG", "DICE_HISTORY",
            "CALENDAR_CONFIGURATION", "CALENDAR_CURRENT_DATE",
            "CUSTOM_COMPENDIUM_NON_STATBLOCK", "STRUCTURED_SCENE_TRANSITIONS",
            "QUESTS_AND_OBJECTIVES");

    @Override
    public int sourceVersion() { return 1; }

    @Override
    public CampaignPackageValidationResult migrate(StagedCampaignPackage source,
                                                     CampaignImportValidator v1Validator) {
        try {
            String json = Files.readString(source.manifestPath(), StandardCharsets.UTF_8);
            var v1Result = v1Validator.validate(json);
            if (!v1Result.valid()) {
                return new CampaignPackageValidationResult(source, null, 1,
                        Map.of(), v1Result.problems(), List.of());
            }
            CampaignExportDto v1 = v1Result.requireImportable();

            List<CampaignImportProblem> warnings = new ArrayList<>();
            List<String> migrations = List.of("MIGRATED_FROM_V1");
            Map<String, Path> assetsByKey = new LinkedHashMap<>();
            List<AssetDescriptor> assetDescriptors = new ArrayList<>();

            var metadata = new CampaignManifestV2.Metadata(
                    "migrated-" + UUID.randomUUID().toString().substring(0, 12),
                    Instant.now(), "DMHelper/0.0.1-SNAPSHOT",
                    "srd-5.2-dmhelper-1",
                    "placeholder-hash",
                    EXCLUSIONS);

            String campaignKey = PackageKeyGenerator.generate(
                    CampaignContentType.CAMPAIGN, v1.campaign().name(), "/campaign");
            var campaign = new CampaignManifestV2.CampaignDto(campaignKey,
                    v1.campaign().name(), v1.campaign().description());

            List<CampaignManifestV2.PartyMemberDto> party = IntStream.range(0, v1.party() != null ? v1.party().size() : 0)
                    .mapToObj(i -> toPartyMember(v1.party().get(i), i)).toList();

            List<CampaignManifestV2.StatBlockDto> statBlocks = IntStream.range(0, v1.statBlocks() != null ? v1.statBlocks().size() : 0)
                    .mapToObj(i -> toStatBlock(v1.statBlocks().get(i), i)).toList();

            List<CampaignManifestV2.HandoutDto> handouts = new ArrayList<>();
            if (v1.handouts() != null) {
                for (int i = 0; i < v1.handouts().size(); i++) {
                    var h = v1.handouts().get(i);
                    String key = PackageKeyGenerator.generate(
                            CampaignContentType.HANDOUT, h.title(), "/handouts/" + i);
                    String assetKey = "handout-" + i;
                    assetDescriptors.add(new AssetDescriptor(assetKey,
                            "assets/handouts/" + assetKey + "." + extensionFor(h.contentType()),
                            h.contentType(), 0, "", h.fileName()));
                    handouts.add(new CampaignManifestV2.HandoutDto(key, h.title(),
                            h.tags(), assetKey, h.contentType()));

                    if (h.imageData() != null && !h.imageData().isEmpty()) {
                        byte[] decoded = decodeDataUrl(h.imageData());
                        if (decoded != null) {
                            String ext = extensionFor(h.contentType());
                            Path assetPath = source.stagingDirectory().resolve("assets").resolve("handouts")
                                    .resolve(assetKey + "." + ext);
                            Files.createDirectories(assetPath.getParent());
                            Files.write(assetPath, decoded);
                            assetsByKey.put(assetKey, assetPath);
                            assetDescriptors.set(assetDescriptors.size() - 1,
                                    new AssetDescriptor(assetKey,
                                            "assets/handouts/" + assetKey + "." + ext,
                                            h.contentType(), decoded.length, sha256(decoded), h.fileName()));
                        }
                    }
                }
            }

            List<CampaignManifestV2.MapDto> maps = new ArrayList<>();
            List<CampaignManifestV2.EncounterDto> encounters = new ArrayList<>();
            List<CampaignManifestV2.NoteDto> notes = new ArrayList<>();
            List<CampaignManifestV2.QuickNoteDto> quickNotes = new ArrayList<>();
            List<CampaignManifestV2.AssignmentDto> assignments = new ArrayList<>();
            List<CampaignManifestV2.LedgerEntryDto> ledgerEntries = new ArrayList<>();
            List<CampaignManifestV2.TimelineEventDto> timelineEvents = new ArrayList<>();
            List<CampaignManifestV2.AdventureDto> adventures = new ArrayList<>();

            var manifest = new CampaignManifestV2(2, metadata, campaign,
                    assetDescriptors, party, statBlocks, handouts, maps,
                    encounters, notes, quickNotes, assignments,
                    ledgerEntries, timelineEvents, adventures);

            return new CampaignPackageValidationResult(source, manifest, 1,
                    assetsByKey, warnings, migrations);
        } catch (IOException e) {
            return new CampaignPackageValidationResult(source, null, 1, Map.of(),
                    List.of(new CampaignImportProblem(ImportSeverity.ERROR,
                            "MIGRATION_ERROR", "", e.getMessage(), null)),
                    List.of());
        }
    }

    private CampaignManifestV2.PartyMemberDto toPartyMember(CampaignExportDto.PartyMemberExportDto p, int idx) {
        String key = PackageKeyGenerator.generate(CampaignContentType.PARTY_MEMBER,
                p.characterName(), "/party/" + idx);
        CampaignManifestV2.SheetDto sheet = null;
        if (p.sheet() != null) {
            var s = p.sheet();
            List<CampaignManifestV2.ClassLevelDto> classes = IntStream.range(0, s.classLevels() != null ? s.classLevels().size() : 0)
                    .mapToObj(i -> toClassLevel(s.classLevels().get(i), idx, i)).toList();
            List<CampaignManifestV2.ResourceDto> resources = IntStream.range(0, s.resources() != null ? s.resources().size() : 0)
                    .mapToObj(i -> toResource(s.resources().get(i), idx, i)).toList();
            List<CampaignManifestV2.SpellRefDto> spells = IntStream.range(0, s.spells() != null ? s.spells().size() : 0)
                    .mapToObj(i -> toSpellRef(s.spells().get(i))).toList();
            ContentReference speciesRef = s.speciesKey() != null
                    ? ContentReference.catalogRef(CampaignContentType.SPECIES, "SRD_5_2", s.speciesKey()) : null;
            ContentReference backgroundRef = s.backgroundKey() != null
                    ? ContentReference.catalogRef(CampaignContentType.BACKGROUND, "SRD_5_2", s.backgroundKey()) : null;
            List<ContentReference> featRefs = s.featRefs() != null
                    ? s.featRefs().stream().map(f -> ContentReference.catalogRef(CampaignContentType.FEAT, "SRD_5_2", f)).toList()
                    : List.of();
            String sheetKey = PackageKeyGenerator.generate(CampaignContentType.CHARACTER_SHEET,
                    key, "/party/" + idx + "/sheet");
            sheet = new CampaignManifestV2.SheetDto(s.abilityScores(), classes, s.proficiencies(),
                    speciesRef, backgroundRef, featRefs, s.xp(), s.overrides(),
                    s.hitDiceUsed(), resources, spells, s.spellSlotsUsed());
        }
        return new CampaignManifestV2.PartyMemberDto(key, p.characterName(), p.playerName(),
                p.classAndLevel(), p.ac(), p.maxHp(), p.initiativeBonus(), p.speed(),
                p.passivePerception(), p.passiveInsight(), p.passiveInvestigation(),
                p.notes(), p.active(), sheet);
    }

    private CampaignManifestV2.ClassLevelDto toClassLevel(CampaignExportDto.ClassLevelExportDto c, int pm, int ci) {
        ContentReference ref = ContentReference.catalogRef(CampaignContentType.CLASS, "SRD_5_2", c.classSourceKey());
        return new CampaignManifestV2.ClassLevelDto(ref, c.level(), c.hitDieRolls());
    }

    private CampaignManifestV2.ResourceDto toResource(CampaignExportDto.ResourceExportDto r, int pm, int ri) {
        return new CampaignManifestV2.ResourceDto(r.name(), r.maxUses(), r.currentUses(), r.resetRule());
    }

    private CampaignManifestV2.SpellRefDto toSpellRef(CampaignExportDto.SpellRefExportDto s) {
        ContentReference spellRef = ContentReference.catalogRef(CampaignContentType.SPELL, "SRD_5_2", s.spellKey());
        ContentReference sourceClassRef = s.sourceClass() != null
                ? ContentReference.catalogRef(CampaignContentType.CLASS, "SRD_5_2", s.sourceClass()) : null;
        return new CampaignManifestV2.SpellRefDto(spellRef, s.prepared(), sourceClassRef);
    }

    private CampaignManifestV2.StatBlockDto toStatBlock(CampaignExportDto.StatBlockExportDto s, int idx) {
        String key = PackageKeyGenerator.generate(CampaignContentType.STATBLOCK,
                s.name(), "/statBlocks/" + idx);
        return new CampaignManifestV2.StatBlockDto(key, s.sourceKey(), s.name(), s.cr(), s.type(),
                s.size(), s.alignment(), s.ac(), s.hp(), s.speed(),
                s.strScore(), s.dexScore(), s.conScore(), s.intScore(), s.wisScore(), s.chaScore(),
                s.strSave(), s.dexSave(), s.conSave(), s.intSave(), s.wisSave(), s.chaSave(),
                s.skills(), s.damageVulnerabilities(), s.damageResistances(),
                s.damageImmunities(), s.conditionImmunities(), s.senses(), s.languages(),
                s.traits(), s.actions(), s.bonusActions(), s.reactions(),
                s.legendaryActions(), s.legendaryDescription(), s.lairActions(), s.xp());
    }

    private static byte[] decodeDataUrl(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:")) return null;
        int commaIdx = dataUrl.indexOf(',');
        if (commaIdx < 0) return null;
        return Base64.getDecoder().decode(dataUrl.substring(commaIdx + 1));
    }

    private static String extensionFor(String mediaType) {
        return switch (mediaType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "bin";
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
