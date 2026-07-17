package dev.hendrikhoemberg.dmhelper.party.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResourceRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReferenceRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
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
    private final SheetResourceRepository sheetResourceRepo;
    private final SheetSpellReferenceRepository sheetSpellRefRepo;
    private final SessionReferenceCleaner sessionRefCleaner;
    private final ItemAssignmentRepository itemAssignmentRepository;
    private final CombatantRepository combatantRepository;
    private final TokenRepository tokenRepository;

    public PartyMemberService(PartyMemberRepository repository, EntityManager em,
                              SheetResourceRepository sheetResourceRepo,
                              SheetSpellReferenceRepository sheetSpellRefRepo,
                              SessionReferenceCleaner sessionRefCleaner,
                              ItemAssignmentRepository itemAssignmentRepository,
                              CombatantRepository combatantRepository,
                              TokenRepository tokenRepository) {
        this.repository = repository;
        this.em = em;
        this.sheetResourceRepo = sheetResourceRepo;
        this.sheetSpellRefRepo = sheetSpellRefRepo;
        this.sessionRefCleaner = sessionRefCleaner;
        this.itemAssignmentRepository = itemAssignmentRepository;
        this.combatantRepository = combatantRepository;
        this.tokenRepository = tokenRepository;
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
        pm.setCurrentHp(maxHp);
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
        // Preserve sheet-derived fields when a character sheet exists
        if (pm.getCharacterSheet() == null) {
            pm.setClassAndLevel(classAndLevel);
            pm.setAc(ac);
            pm.setMaxHp(maxHp);
            pm.setInitiativeBonus(initiativeBonus);
            pm.setSpeed(speed);
            pm.setPassivePerception(passivePerception);
            pm.setPassiveInsight(passiveInsight);
            pm.setPassiveInvestigation(passiveInvestigation);
        }
        pm.setNotes(notes);
        if (pm.getCurrentHp() > pm.getMaxHp()) pm.setCurrentHp(pm.getMaxHp());
        return repository.save(pm);
    }

    public void delete(UUID id) {
        sessionRefCleaner.detachAttendee(id);
        PartyMember pm = findById(id);
        for (var assignment : itemAssignmentRepository.findByPartyMemberId(id)) {
            assignment.setPartyMember(null);
            itemAssignmentRepository.save(assignment);
        }
        for (var combatant : combatantRepository.findByPartyMemberId(id)) {
            combatant.setPartyMember(null);
            combatantRepository.save(combatant);
        }
        for (var token : tokenRepository.findByPartyMemberId(id)) {
            token.setPartyMember(null);
            tokenRepository.save(token);
        }
        if (pm.getCharacterSheet() != null) {
            UUID sheetId = pm.getCharacterSheet().getId();
            sheetResourceRepo.deleteBySheetId(sheetId);
            sheetSpellRefRepo.deleteBySheetId(sheetId);
        }
        repository.delete(pm);
    }

    public void setActive(UUID id, boolean active) {
        PartyMember pm = findById(id);
        pm.setActive(active);
        repository.save(pm);
    }

    public PartyMember updateLiveState(UUID partyMemberId, PartyLiveStateDto body) {
        if (body.exhaustion() < 0 || body.exhaustion() > 6) {
            throw new IllegalArgumentException("Exhaustion must be 0–6");
        }
        if (body.deathSaveSuccesses() < 0 || body.deathSaveSuccesses() > 3) {
            throw new IllegalArgumentException("Death save successes must be 0–3");
        }
        if (body.deathSaveFailures() < 0 || body.deathSaveFailures() > 3) {
            throw new IllegalArgumentException("Death save failures must be 0–3");
        }
        if (body.tempHp() < 0) {
            throw new IllegalArgumentException("Temp HP cannot be negative");
        }

        PartyMember pm = findById(partyMemberId);
        pm.setTempHp(body.tempHp());
        pm.setInspiration(body.inspiration());
        pm.setExhaustion(body.exhaustion());
        pm.setDeathSaveSuccesses(body.deathSaveSuccesses());
        pm.setDeathSaveFailures(body.deathSaveFailures());
        pm.setConcentratingOn(body.concentratingOn());
        pm.setConditionsJson(body.conditionsJson());
        if (body.currentHp() != null) {
            int max = body.maxHp() != null ? body.maxHp() : pm.getMaxHp();
            pm.setCurrentHp(Math.min(body.currentHp(), max));
        }
        if (body.maxHp() != null) {
            pm.setMaxHp(body.maxHp());
            if (pm.getCurrentHp() > pm.getMaxHp()) {
                pm.setCurrentHp(pm.getMaxHp());
            }
        }
        return repository.save(pm);
    }

    public record PartyLiveStateDto(
            int tempHp,
            boolean inspiration,
            int exhaustion,
            int deathSaveSuccesses,
            int deathSaveFailures,
            String concentratingOn,
            String conditionsJson,
            Integer currentHp,
            Integer maxHp
    ) {}
}
