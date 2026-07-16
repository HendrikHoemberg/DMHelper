package dev.hendrikhoemberg.dmhelper.library.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.StatBlockDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LibrarySectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final StatBlockRepository repository;

    public LibrarySectionAdapter(StatBlockRepository repository) {
        this.repository = repository;
    }

    @Override
    public String sectionName() {
        return "Library";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        List<StatBlock> customBlocks = repository.findByCampaignIdOrderByNameAsc(context.campaignId())
                .stream()
                .filter(sb -> sb.getSource() == StatBlock.Source.CUSTOM)
                .toList();

        List<StatBlockDto> dtos = customBlocks.stream()
                .map(sb -> {
                    String key = context.key(CampaignContentType.STATBLOCK, sb.getId(), sb.getName());
                    return new StatBlockDto(
                            key, sb.getSourceKey(), sb.getName(), sb.getCr(), sb.getType(),
                            sb.getSize(), sb.getAlignment(), sb.getAc(), sb.getHp(), sb.getSpeed(),
                            sb.getStrScore(), sb.getDexScore(), sb.getConScore(), sb.getIntScore(),
                            sb.getWisScore(), sb.getChaScore(),
                            sb.getStrSave(), sb.getDexSave(), sb.getConSave(), sb.getIntSave(),
                            sb.getWisSave(), sb.getChaSave(),
                            sb.getSkills(), sb.getDamageVulnerabilities(), sb.getDamageResistances(),
                            sb.getDamageImmunities(), sb.getConditionImmunities(),
                            sb.getSenses(), sb.getLanguages(),
                            sb.getTraits(), sb.getActions(), sb.getBonusActions(), sb.getReactions(),
                            sb.getLegendaryActions(), sb.getLegendaryDescription(), sb.getLairActions(),
                            sb.getXp(), sb.getCreatedAt()
                    );
                })
                .toList();

        target.customStatBlocks(dtos);
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<StatBlockDto> dtos = source.customStatBlocks();
        if (dtos == null) return;

        var campaign = context.campaign();

        for (StatBlockDto dto : dtos) {
            var sb = new StatBlock();
            sb.setSource(StatBlock.Source.CUSTOM);
            sb.setCampaign(campaign);
            sb.setName(dto.name());
            sb.setCr(dto.cr());
            sb.setType(dto.type());
            sb.setSize(dto.size());
            sb.setAlignment(dto.alignment());
            sb.setAc(dto.ac());
            sb.setHp(dto.hp());
            sb.setSpeed(dto.speed());
            sb.setStrScore(dto.strScore());
            sb.setDexScore(dto.dexScore());
            sb.setConScore(dto.conScore());
            sb.setIntScore(dto.intScore());
            sb.setWisScore(dto.wisScore());
            sb.setChaScore(dto.chaScore());
            sb.setStrSave(dto.strSave());
            sb.setDexSave(dto.dexSave());
            sb.setConSave(dto.conSave());
            sb.setIntSave(dto.intSave());
            sb.setWisSave(dto.wisSave());
            sb.setChaSave(dto.chaSave());
            sb.setSkills(dto.skills());
            sb.setDamageVulnerabilities(dto.damageVulnerabilities());
            sb.setDamageResistances(dto.damageResistances());
            sb.setDamageImmunities(dto.damageImmunities());
            sb.setConditionImmunities(dto.conditionImmunities());
            sb.setSenses(dto.senses());
            sb.setLanguages(dto.languages());
            sb.setTraits(dto.traits());
            sb.setActions(dto.actions());
            sb.setBonusActions(dto.bonusActions());
            sb.setReactions(dto.reactions());
            sb.setLegendaryActions(dto.legendaryActions());
            sb.setLegendaryDescription(dto.legendaryDescription());
            sb.setLairActions(dto.lairActions());
            sb.setXp(dto.xp());
            if (dto.sourceKey() != null && !dto.sourceKey().isBlank()) {
                sb.setSourceKey(dto.sourceKey());
            }
            repository.save(sb);
            context.register(CampaignContentType.STATBLOCK, dto.key(), sb, sb.getId());
        }
    }
}
