package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class StatBlockService {

    private final StatBlockRepository repository;
    private final CampaignRepository campaignRepo;

    public StatBlockService(StatBlockRepository repository, CampaignRepository campaignRepo) {
        this.repository = repository;
        this.campaignRepo = campaignRepo;
    }

    @Transactional(readOnly = true)
    public List<StatBlock> search(StatBlock.Source source, String cr, String type, String search) {
        return repository.findAll((root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (source != null) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (cr != null && !cr.isBlank()) {
                predicates.add(cb.equal(root.get("cr"), cr));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + search.toLowerCase() + "%"));
            }

            query.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    @Transactional(readOnly = true)
    public StatBlock findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("StatBlock not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<StatBlock> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByNameAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public List<StatBlock> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public StatBlock createCustom(UUID campaignId, String name, String cr, String type,
                                  int ac, String hp, String speed,
                                  int str, int dex, int con, int intel, int wis, int cha,
                                   Integer strSave, Integer dexSave, Integer conSave,
                                   Integer intSave, Integer wisSave, Integer chaSave,
                                   String skills, String damageVuln, String damageRes,
                                   String damageImm, String condImm,
                                   String senses, String languages) {
        StatBlock sb = new StatBlock();
        sb.setSource(StatBlock.Source.CUSTOM);
        if (campaignId != null) {
            campaignRepo.findById(campaignId).ifPresent(sb::setCampaign);
        }
        sb.setName(name);
        sb.setCr(cr);
        sb.setType(type);
        sb.setAc(ac);
        sb.setHp(hp);
        sb.setSpeed(speed);
        sb.setStrScore(str);
        sb.setDexScore(dex);
        sb.setConScore(con);
        sb.setIntScore(intel);
        sb.setWisScore(wis);
        sb.setChaScore(cha);
        sb.setStrSave(strSave);
        sb.setDexSave(dexSave);
        sb.setConSave(conSave);
        sb.setIntSave(intSave);
        sb.setWisSave(wisSave);
        sb.setChaSave(chaSave);
        sb.setSkills(skills);
        sb.setDamageVulnerabilities(damageVuln);
        sb.setDamageResistances(damageRes);
        sb.setDamageImmunities(damageImm);
        sb.setConditionImmunities(condImm);
        sb.setSenses(senses);
        sb.setLanguages(languages);
        return repository.save(sb);
    }

    public StatBlock updateCustom(UUID id, String name, String cr, String type,
                                  int ac, String hp, String speed,
                                   int str, int dex, int con, int intel, int wis, int cha,
                                   Integer strSave, Integer dexSave, Integer conSave,
                                   Integer intSave, Integer wisSave, Integer chaSave,
                                   String skills, String damageVuln, String damageRes,
                                   String damageImm, String condImm,
                                   String senses, String languages) {
        StatBlock sb = findById(id);
        if (sb.getSource() != StatBlock.Source.CUSTOM) {
            throw new IllegalArgumentException("Cannot edit SRD statblocks");
        }
        sb.setName(name);
        sb.setCr(cr);
        sb.setType(type);
        sb.setAc(ac);
        sb.setHp(hp);
        sb.setSpeed(speed);
        sb.setStrScore(str);
        sb.setDexScore(dex);
        sb.setConScore(con);
        sb.setIntScore(intel);
        sb.setWisScore(wis);
        sb.setChaScore(cha);
        sb.setStrSave(strSave);
        sb.setDexSave(dexSave);
        sb.setConSave(conSave);
        sb.setIntSave(intSave);
        sb.setWisSave(wisSave);
        sb.setChaSave(chaSave);
        sb.setSkills(skills);
        sb.setDamageVulnerabilities(damageVuln);
        sb.setDamageResistances(damageRes);
        sb.setDamageImmunities(damageImm);
        sb.setConditionImmunities(condImm);
        sb.setSenses(senses);
        sb.setLanguages(languages);
        return repository.save(sb);
    }

    public void delete(UUID id) {
        StatBlock sb = findById(id);
        if (sb.getSource() != StatBlock.Source.CUSTOM) {
            throw new IllegalArgumentException("Cannot delete SRD statblocks");
        }
        repository.delete(sb);
    }

    public StatBlock cloneAsCustom(UUID sourceId, UUID targetCampaignId, String newName) {
        StatBlock original = findById(sourceId);
        StatBlock clone = new StatBlock();
        clone.setSource(StatBlock.Source.CUSTOM);
        if (targetCampaignId != null) {
            campaignRepo.findById(targetCampaignId).ifPresent(clone::setCampaign);
        }
        clone.setName(newName != null && !newName.isBlank() ? newName : original.getName());
        clone.setCr(original.getCr());
        clone.setType(original.getType());
        clone.setSize(original.getSize());
        clone.setAlignment(original.getAlignment());
        clone.setAc(original.getAc());
        clone.setHp(original.getHp());
        clone.setSpeed(original.getSpeed());
        clone.setStrScore(original.getStrScore());
        clone.setDexScore(original.getDexScore());
        clone.setConScore(original.getConScore());
        clone.setIntScore(original.getIntScore());
        clone.setWisScore(original.getWisScore());
        clone.setChaScore(original.getChaScore());
        clone.setStrSave(original.getStrSave());
        clone.setDexSave(original.getDexSave());
        clone.setConSave(original.getConSave());
        clone.setIntSave(original.getIntSave());
        clone.setWisSave(original.getWisSave());
        clone.setChaSave(original.getChaSave());
        clone.setSkills(original.getSkills());
        clone.setDamageVulnerabilities(original.getDamageVulnerabilities());
        clone.setDamageResistances(original.getDamageResistances());
        clone.setDamageImmunities(original.getDamageImmunities());
        clone.setConditionImmunities(original.getConditionImmunities());
        clone.setSenses(original.getSenses());
        clone.setLanguages(original.getLanguages());
        clone.setTraits(original.getTraits());
        clone.setActions(original.getActions());
        clone.setBonusActions(original.getBonusActions());
        clone.setReactions(original.getReactions());
        clone.setLegendaryActions(original.getLegendaryActions());
        clone.setLegendaryDescription(original.getLegendaryDescription());
        clone.setLairActions(original.getLairActions());
        clone.setXp(original.getXp());
        return repository.save(clone);
    }

    public StatBlock promoteToGlobal(UUID id) {
        StatBlock sb = findById(id);
        if (sb.getSource() != StatBlock.Source.CUSTOM) {
            throw new IllegalArgumentException("Only custom statblocks can be promoted");
        }
        sb.setCampaign(null);
        return repository.save(sb);
    }
}
