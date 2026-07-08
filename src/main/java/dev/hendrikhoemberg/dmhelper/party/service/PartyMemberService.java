package dev.hendrikhoemberg.dmhelper.party.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PartyMemberService {

    private final PartyMemberRepository repository;
    private final EntityManager em;

    public PartyMemberService(PartyMemberRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    public PartyMember create(UUID campaignId, String characterName, String playerName,
                              String classAndLevel, int ac, int maxHp, int initiativeBonus,
                              int speed, int passivePerception, int passiveInsight,
                              int passiveInvestigation, String notes) {
        Campaign campaign = em.getReference(Campaign.class, campaignId);
        PartyMember pm = new PartyMember();
        pm.setCampaign(campaign);
        pm.setCharacterName(characterName);
        pm.setPlayerName(playerName);
        pm.setClassAndLevel(classAndLevel);
        pm.setAc(ac);
        pm.setMaxHp(maxHp);
        pm.setInitiativeBonus(initiativeBonus);
        pm.setSpeed(speed);
        pm.setPassivePerception(passivePerception);
        pm.setPassiveInsight(passiveInsight);
        pm.setPassiveInvestigation(passiveInvestigation);
        pm.setNotes(notes);
        pm.setActive(true);
        return repository.save(pm);
    }

    @Transactional(readOnly = true)
    public List<PartyMember> findByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdOrderByCharacterNameAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public List<PartyMember> findActiveByCampaignId(UUID campaignId) {
        return repository.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public PartyMember findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Party member not found: " + id));
    }

    public PartyMember update(UUID id, String characterName, String playerName,
                               String classAndLevel, int ac, int maxHp, int initiativeBonus,
                               int speed, int passivePerception, int passiveInsight,
                               int passiveInvestigation, String notes) {
        PartyMember pm = findById(id);
        pm.setCharacterName(characterName);
        pm.setPlayerName(playerName);
        pm.setClassAndLevel(classAndLevel);
        // Preserve sheet-derived combat fields when a character sheet exists
        if (pm.getCharacterSheet() == null) {
            pm.setAc(ac);
            pm.setMaxHp(maxHp);
            pm.setInitiativeBonus(initiativeBonus);
            pm.setSpeed(speed);
            pm.setPassivePerception(passivePerception);
            pm.setPassiveInsight(passiveInsight);
            pm.setPassiveInvestigation(passiveInvestigation);
        }
        pm.setNotes(notes);
        return repository.save(pm);
    }

    public void delete(UUID id) {
        PartyMember pm = findById(id);
        repository.delete(pm);
    }

    public void setActive(UUID id, boolean active) {
        PartyMember pm = findById(id);
        pm.setActive(active);
        repository.save(pm);
    }
}
