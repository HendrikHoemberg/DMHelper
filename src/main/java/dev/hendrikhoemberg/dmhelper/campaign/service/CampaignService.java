package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEventRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;

import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatLogEntryRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLink;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CampaignService {

    private final CampaignRepository repository;
    private final PartyMemberRepository partyMemberRepository;
    private final StatBlockRepository statBlockRepository;
    private final PartyMemberService partyMemberService;
    private final GameMapService gameMapService;
    private final NoteRepository noteRepository;
    private final QuickNoteRepository quickNoteRepository;
    private final NoteService noteService;
    private final ItemAssignmentRepository assignmentRepo;
    private final LedgerEntryRepository ledgerEntryRepo;
    private final TimelineEventRepository timelineEventRepo;
    private final EncounterRepository encounterRepo;
    private final CombatantRepository combatantRepo;
    private final CombatLogEntryRepository combatLogEntryRepo;
    private final HandoutService handoutService;
    private final HandoutRepository handoutRepo;
    private final TokenRepository tokenRepo;
    private final dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository adventureRepo;
    private final dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository chapterRepo;
    private final dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository sceneRepo;
    private final dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository rollableTableRepo;
    private final dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository diceRollRepo;
    private final CampaignSessionRepository campaignSessionRepository;
    private final SessionSceneVisitRepository sessionSceneVisitRepository;
    private final dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner sessionReferenceCleaner;
    private final dev.hendrikhoemberg.dmhelper.world.data.FactionClockRepository factionClockRepo;
    private final dev.hendrikhoemberg.dmhelper.world.data.WorldRelationshipRepository worldRelationshipRepo;
    private final dev.hendrikhoemberg.dmhelper.world.data.WorldNpcRepository worldNpcRepo;
    private final dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository worldLocationRepo;
    private final dev.hendrikhoemberg.dmhelper.world.data.FactionRepository worldFactionRepo;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    public CampaignService(CampaignRepository repository,
                           PartyMemberRepository partyMemberRepository,
                           StatBlockRepository statBlockRepository,
                           PartyMemberService partyMemberService,
                           GameMapService gameMapService,
                           NoteRepository noteRepository,
                           QuickNoteRepository quickNoteRepository,
                           NoteService noteService,
                           ItemAssignmentRepository assignmentRepo,
                           LedgerEntryRepository ledgerEntryRepo,
                           TimelineEventRepository timelineEventRepo,
                             EncounterRepository encounterRepo,
                             CombatantRepository combatantRepo,
                             CombatLogEntryRepository combatLogEntryRepo,
                             HandoutService handoutService,
                             HandoutRepository handoutRepo,
                             TokenRepository tokenRepo,
                             dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository adventureRepo,
                             dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository chapterRepo,
                              dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository sceneRepo,
                              dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository rollableTableRepo,
                              dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository diceRollRepo,
                              CampaignSessionRepository campaignSessionRepository,
                              SessionSceneVisitRepository sessionSceneVisitRepository,
                               dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner sessionReferenceCleaner,
                               dev.hendrikhoemberg.dmhelper.world.data.FactionClockRepository factionClockRepo,
                               dev.hendrikhoemberg.dmhelper.world.data.WorldRelationshipRepository worldRelationshipRepo,
                               dev.hendrikhoemberg.dmhelper.world.data.WorldNpcRepository worldNpcRepo,
                               dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository worldLocationRepo,
                               dev.hendrikhoemberg.dmhelper.world.data.FactionRepository worldFactionRepo) {
        this.repository = repository;
        this.partyMemberRepository = partyMemberRepository;
        this.statBlockRepository = statBlockRepository;
        this.partyMemberService = partyMemberService;
        this.gameMapService = gameMapService;
        this.noteRepository = noteRepository;
        this.quickNoteRepository = quickNoteRepository;
        this.noteService = noteService;
        this.assignmentRepo = assignmentRepo;
        this.ledgerEntryRepo = ledgerEntryRepo;
        this.timelineEventRepo = timelineEventRepo;
        this.encounterRepo = encounterRepo;
        this.combatantRepo = combatantRepo;
        this.combatLogEntryRepo = combatLogEntryRepo;
        this.handoutService = handoutService;
        this.handoutRepo = handoutRepo;
        this.tokenRepo = tokenRepo;
        this.adventureRepo = adventureRepo;
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
        this.rollableTableRepo = rollableTableRepo;
        this.diceRollRepo = diceRollRepo;
        this.campaignSessionRepository = campaignSessionRepository;
        this.sessionSceneVisitRepository = sessionSceneVisitRepository;
        this.sessionReferenceCleaner = sessionReferenceCleaner;
        this.factionClockRepo = factionClockRepo;
        this.worldRelationshipRepo = worldRelationshipRepo;
        this.worldNpcRepo = worldNpcRepo;
        this.worldLocationRepo = worldLocationRepo;
        this.worldFactionRepo = worldFactionRepo;
    }

    public Campaign create(String name, String description) {
        return create(name, description, null);
    }

    public Campaign create(String name, String description, java.time.Instant createdAt) {
        Campaign campaign = new Campaign();
        campaign.setName(name);
        campaign.setDescription(description);
        campaign.setCreatedAt(createdAt);
        return repository.save(campaign);
    }

    @Transactional(readOnly = true)
    public List<Campaign> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Campaign findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + id));
    }

    public Campaign update(UUID id, String name, String description) {
        Campaign campaign = findById(id);
        campaign.setName(name);
        campaign.setDescription(description);
        return repository.save(campaign);
    }

    /**
     * Deleting a campaign takes its whole world with it.
     *
     * <p>Nothing in the schema cascades: every child row holds a foreign key back to the
     * campaign, so any survivor blocks the delete outright. Order is load-bearing, and the
     * flush between each stage is what makes it real — Hibernate is free to reorder queued
     * deletes within a single flush, and a delete that reaches the database out of order
     * trips the very constraint this method exists to respect.
     */
    public void delete(UUID id) {
        Campaign campaign = findById(id);
        UUID cid = campaign.getId();
        sessionReferenceCleaner.detachCampaign(cid);

        // The coordination aggregate points back into scenes, maps, notes, handouts, and party.
        // Remove it first so those established child-deletion paths remain valid.
        campaignSessionRepository.findByCampaignId(cid).ifPresent(session -> {
            var audioStates = em.createQuery(
                    "select s from SessionAudioState s where s.session.id = :sid",
                    dev.hendrikhoemberg.dmhelper.audio.data.SessionAudioState.class)
                    .setParameter("sid", session.getId())
                    .getResultList();
            audioStates.forEach(em::remove);
            sessionSceneVisitRepository.deleteBySessionId(session.getId());
            campaignSessionRepository.delete(session);
        });
        em.flush();

        // Adventures first: scenes point at maps, encounters, handouts and statblocks, and
        // those references pin everything else in place until the story is gone.
        for (var adventure : adventureRepo.findByCampaignIdOrderBySortOrderAsc(cid)) {
            for (var chapter : chapterRepo.findByAdventureIdOrderBySortOrderAsc(adventure.getId())) {
                sceneRepo.deleteAll(sceneRepo.findByChapterIdOrderBySortOrderAsc(chapter.getId()));
                chapterRepo.delete(chapter);
            }
            adventureRepo.delete(adventure);
        }
        em.flush();

        // Combatants reference party members, statblocks and tokens — all of which outlive them here.
        var encounters = encounterRepo.findByCampaignIdOrderByNameAsc(cid);
        for (var encounter : encounters) {
            combatLogEntryRepo.deleteByEncounterId(encounter.getId());
            combatantRepo.deleteByEncounterId(encounter.getId());
        }
        em.flush();
        encounterRepo.deleteAll(encounters);
        em.flush();

        for (var map : gameMapService.findByCampaignId(cid)) {
            tokenRepo.deleteByMapId(map.getId());
            em.flush();
            gameMapService.delete(map.getId());
        }
        em.flush();

        // Ledger rows reference assignments, and timeline rows may reference notes.
        ledgerEntryRepo.deleteAll(ledgerEntryRepo.findByCampaignIdOrderByTimestampDesc(cid));
        timelineEventRepo.deleteAll(
                timelineEventRepo.findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(cid));
        em.flush();

        // Treasury assignments name party members, so they go before the party does.
        assignmentRepo.deleteAll(assignmentRepo.findByCampaignIdOrderByPartyMemberAsc(cid));
        em.flush();

        // Character sheets ride along on the party member (cascade + orphanRemoval).
        for (var member : partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(cid)) {
            partyMemberService.delete(member.getId());
        }
        em.flush();

        statBlockRepository.deleteAll(statBlockRepository.findByCampaignIdOrderByNameAsc(cid));
        em.flush();

        // noteService.delete also clears the wiki links leaving each note.
        for (var note : noteRepository.findByCampaignIdOrderByCreatedAtDesc(cid)) {
            noteService.delete(note.getId());
        }
        quickNoteRepository.deleteAll(quickNoteRepository.findByCampaignIdOrderByCreatedAtDesc(cid));
        em.flush();

        // handoutService.delete takes the uploaded file with the row.
        for (var handout : handoutRepo.findByCampaignIdOrderByTitleAsc(cid)) {
            handoutService.delete(handout.getId());
        }
        em.flush();

        diceRollRepo.deleteAll(diceRollRepo.findByCampaignId(cid));
        em.flush();

        em.createQuery("delete from WorldLocationTableLink l where l.location.id in "
                + "(select loc.id from WorldLocation loc where loc.campaign.id = :cid)")
                .setParameter("cid", cid).executeUpdate();
        em.flush();
        rollableTableRepo.deleteAll(rollableTableRepo.findByCampaignIdOrderByNameAsc(cid));
        em.flush();

        // World graph — clocks reference factions (non-null FK), so delete clocks first.
        factionClockRepo.deleteAll(factionClockRepo.findByCampaignIdOrderBySortOrderAscIdAsc(cid));
        em.flush();

        worldRelationshipRepo.deleteAll(
                worldRelationshipRepo.findByCampaignIdOrderBySortOrderAscIdAsc(cid));
        em.flush();

        // Null out parent-location self-refs so Hibernate can delete in any order.
        var locations = worldLocationRepo.findByCampaignIdOrderByNameAscIdAsc(cid);
        for (var loc : locations) {
            loc.setParentLocation(null);
        }
        worldLocationRepo.saveAll(locations);
        em.flush();
        worldLocationRepo.deleteAll(locations);
        em.flush();

        worldNpcRepo.deleteAll(worldNpcRepo.findByCampaignIdOrderByNameAscIdAsc(cid));
        em.flush();

        worldFactionRepo.deleteAll(worldFactionRepo.findByCampaignIdOrderByNameAscIdAsc(cid));
        em.flush();

        // The campaign can row-reference an audio cue as its default. Null the FK out so the
        // DELETE does not trip FK_CAMPAIGN_DEFAULT_AUDIO_CUE.
        campaign.setDefaultAudioCue(null);
        em.flush();

        repository.delete(campaign);
    }

    public void setMilestoneMode(UUID campaignId, boolean enabled) {
        Campaign c = findById(campaignId);
        c.setMilestoneLeveling(enabled);
        repository.save(c);
    }

}
