package dev.hendrikhoemberg.dmhelper.quest.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope;
import dev.hendrikhoemberg.dmhelper.quest.data.*;
import dev.hendrikhoemberg.dmhelper.session.service.SessionActivityRecorder;
import dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestServiceTest {

    @Mock private QuestRepository questRepository;
    @Mock private QuestObjectiveRepository objectiveRepository;
    @Mock private QuestObjectiveDependencyRepository dependencyRepository;
    @Mock private QuestLinkRepository linkRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private SessionActivityRecorder activityRecorder;
    @Mock private QuestObjectiveDependencyValidator dependencyValidator;
    @Mock private CampaignPackageKeyService packageKeys;
    @Mock private SessionReferenceCleaner sessionRefCleaner;

    @InjectMocks private QuestService service;

    @Captor private ArgumentCaptor<Quest> questCaptor;
    @Captor private ArgumentCaptor<QuestObjective> objectiveCaptor;
    @Captor private ArgumentCaptor<QuestLink> linkCaptor;

    private UUID campaignId;
    private UUID questId;
    private Quest quest;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
        questId = UUID.randomUUID();
        quest = new Quest();
        quest.setId(questId);
        quest.setTitle("Original Title");
        quest.setStatus(QuestStatus.NOT_STARTED);
        quest.setTags("a, b");
        var campaign = new dev.hendrikhoemberg.dmhelper.campaign.data.Campaign();
        campaign.setId(campaignId);
        quest.setCampaign(campaign);
    }

    // ---- Quest CRUD ----

    @Test
    void createsQuest() {
        var cmd = new QuestService.QuestCommand("New Quest", QuestStatus.NOT_STARTED, "Summary",
                "src:test", "tag1, tag2", "xp", "lvl5+", "notes");
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(new dev.hendrikhoemberg.dmhelper.campaign.data.Campaign()));
        when(questRepository.save(any())).thenAnswer(invocation -> {
            Quest q = invocation.getArgument(0);
            q.setId(questId);
            return q;
        });

        Quest result = service.createQuest(campaignId, cmd);

        assertThat(result.getId()).isEqualTo(questId);
        assertThat(result.getTitle()).isEqualTo("New Quest");
        assertThat(result.getStatus()).isEqualTo(QuestStatus.NOT_STARTED);
        assertThat(result.getTags()).isEqualTo("tag1, tag2");
        verify(questRepository).save(questCaptor.capture());
        assertThat(questCaptor.getValue().getCampaign().getId()).isEqualTo(campaignId);
    }

    @Test
    void normalizesTagsOnCreate() {
        var cmd = new QuestService.QuestCommand("Tags", QuestStatus.NOT_STARTED,
                null, null, "  messy ,  tags ,, here ", null, null, null);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(new dev.hendrikhoemberg.dmhelper.campaign.data.Campaign()));
        when(questRepository.save(any())).thenAnswer(inv -> {
            Quest q = inv.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        Quest result = service.createQuest(campaignId, cmd);

        assertThat(result.getTags()).isEqualTo("messy, tags, here");
    }

    @Test
    void updatesQuest() {
        var cmd = new QuestService.QuestCommand("Updated Title", QuestStatus.ACTIVE,
                "New summary", "src:new", "x, y", "gp", null, "done");
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        when(questRepository.save(quest)).thenReturn(quest);

        Quest result = service.updateQuest(campaignId, questId, cmd);

        assertThat(result.getTitle()).isEqualTo("Updated Title");
        assertThat(result.getStatus()).isEqualTo(QuestStatus.ACTIVE);
        assertThat(result.getSummary()).isEqualTo("New summary");
        assertThat(result.getSourceLocator()).isEqualTo("src:new");
        assertThat(result.getTags()).isEqualTo("x, y");
        assertThat(result.getRewards()).isEqualTo("gp");
        assertThat(result.getOutcomeNotes()).isEqualTo("done");
    }

    @Test
    void throwsNotFoundOnUpdateForWrongCampaign() {
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.empty());
        var cmd = new QuestService.QuestCommand("X", QuestStatus.NOT_STARTED, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.updateQuest(campaignId, questId, cmd))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletesQuest() {
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));

        service.deleteQuest(campaignId, questId);

        verify(questRepository).delete(quest);
        verify(packageKeys).deleteBindings(campaignId, CampaignContentType.QUEST, List.of(questId));
    }

    @Test
    void getsQuests() {
        when(questRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId)).thenReturn(List.of(quest));
        List<Quest> result = service.getQuests(campaignId);
        assertThat(result).containsExactly(quest);
    }

    @Test
    void getsQuest() {
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        Quest result = service.getQuest(campaignId, questId);
        assertThat(result).isEqualTo(quest);
    }

    @Test
    void throwsNotFoundOnGetQuestForWrongCampaign() {
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getQuest(campaignId, questId))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- Objective management ----

    @Test
    void addsObjective() {
        var cmd = new QuestService.QuestObjectiveCommand("Obj 1", "desc",
                QuestObjectiveStatus.NOT_STARTED, QuestObjectiveCompletionMode.ALL, 0, "src:obj");
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        when(objectiveRepository.save(any())).thenAnswer(inv -> {
            QuestObjective o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        QuestObjective result = service.addObjective(campaignId, questId, cmd);

        assertThat(result.getTitle()).isEqualTo("Obj 1");
        assertThat(result.getDescription()).isEqualTo("desc");
        assertThat(result.getQuest().getId()).isEqualTo(questId);
    }

    @Test
    void updatesObjective() {
        UUID objId = UUID.randomUUID();
        QuestObjective obj = new QuestObjective();
        obj.setId(objId);
        obj.setQuest(quest);
        obj.setTitle("Old");
        quest.getObjectives().add(obj);

        var cmd = new QuestService.QuestObjectiveCommand("New Title", "new desc",
                QuestObjectiveStatus.COMPLETED, QuestObjectiveCompletionMode.ANY, 5, "src:new");
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        when(objectiveRepository.save(obj)).thenReturn(obj);

        QuestObjective result = service.updateObjective(campaignId, questId, objId, cmd);

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getDescription()).isEqualTo("new desc");
        assertThat(result.getStatus()).isEqualTo(QuestObjectiveStatus.COMPLETED);
        assertThat(result.getCompletionMode()).isEqualTo(QuestObjectiveCompletionMode.ANY);
        assertThat(result.getSortOrder()).isEqualTo(5);
        assertThat(result.getSourceLocator()).isEqualTo("src:new");
    }

    @Test
    void deletesObjectiveAndRenumbers() {
        UUID keepId = UUID.randomUUID();
        UUID deleteId = UUID.randomUUID();
        QuestObjective keep = new QuestObjective();
        keep.setId(keepId);
        keep.setSortOrder(0);
        keep.setQuest(quest);
        QuestObjective delete = new QuestObjective();
        delete.setId(deleteId);
        delete.setSortOrder(1);
        delete.setQuest(quest);
        quest.getObjectives().add(keep);
        quest.getObjectives().add(delete);

        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));

        service.deleteObjective(campaignId, questId, deleteId);

        verify(objectiveRepository).delete(delete);
        assertThat(keep.getSortOrder()).isZero();
        verify(packageKeys).deleteBindings(campaignId, CampaignContentType.OBJECTIVE, List.of(deleteId));
    }

    @Test
    void setsObjectiveStatusAndRecordsChange() {
        UUID objId = UUID.randomUUID();
        QuestObjective obj = new QuestObjective();
        obj.setId(objId);
        obj.setQuest(quest);
        obj.setStatus(QuestObjectiveStatus.NOT_STARTED);
        quest.getObjectives().add(obj);

        when(objectiveRepository.findById(objId)).thenReturn(Optional.of(obj));
        when(objectiveRepository.save(obj)).thenReturn(obj);

        when(activityRecorder.recordObjectiveChange(campaignId, objId,
                QuestObjectiveStatus.NOT_STARTED, QuestObjectiveStatus.ACTIVE))
                .thenReturn(Optional.of(UUID.randomUUID()));

        QuestService.ObjectiveStatusUpdate result =
                service.setObjectiveStatus(campaignId, objId, QuestObjectiveStatus.ACTIVE);

        assertThat(result.objective().getStatus()).isEqualTo(QuestObjectiveStatus.ACTIVE);
        assertThat(result.sessionChangeId()).isNotNull();
        verify(activityRecorder).recordObjectiveChange(campaignId, objId,
                QuestObjectiveStatus.NOT_STARTED, QuestObjectiveStatus.ACTIVE);
    }

    @Test
    void doesNotRecordChangeWhenStatusUnchanged() {
        UUID objId = UUID.randomUUID();
        QuestObjective obj = new QuestObjective();
        obj.setId(objId);
        obj.setQuest(quest);
        obj.setStatus(QuestObjectiveStatus.ACTIVE);
        quest.getObjectives().add(obj);

        when(objectiveRepository.findById(objId)).thenReturn(Optional.of(obj));

        QuestService.ObjectiveStatusUpdate result =
                service.setObjectiveStatus(campaignId, objId, QuestObjectiveStatus.ACTIVE);

        assertThat(result.sessionChangeId()).isNull();
        verify(objectiveRepository, never()).save(any());
        verify(activityRecorder, never()).recordObjectiveChange(any(), any(), any(), any());
    }

    // ---- Dependencies ----

    @Test
    void addsDependency() {
        UUID objId = UUID.randomUUID();
        UUID prereqId = UUID.randomUUID();
        QuestObjective obj = new QuestObjective();
        obj.setId(objId);
        obj.setQuest(quest);
        QuestObjective prereq = new QuestObjective();
        prereq.setId(prereqId);
        prereq.setQuest(quest);
        quest.getObjectives().addAll(List.of(obj, prereq));

        when(objectiveRepository.findById(objId)).thenReturn(Optional.of(obj));
        when(objectiveRepository.findById(prereqId)).thenReturn(Optional.of(prereq));
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        when(dependencyRepository.findByQuestId(questId)).thenReturn(List.of());
        when(dependencyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuestObjectiveDependency result = service.addDependency(campaignId, objId, prereqId);

        assertThat(result.getObjective().getId()).isEqualTo(objId);
        assertThat(result.getPrerequisiteObjective().getId()).isEqualTo(prereqId);
        assertThat(result.getQuest().getId()).isEqualTo(questId);
        verify(dependencyValidator).validate(objId, prereqId, List.of());
    }

    @Test
    void rejectsObjectiveFromDifferentQuest() {
        UUID objId = UUID.randomUUID();
        UUID prereqId = UUID.randomUUID();
        Quest otherQuest = new Quest();
        otherQuest.setId(UUID.randomUUID());
        QuestObjective obj = new QuestObjective();
        obj.setId(objId);
        obj.setQuest(quest);
        QuestObjective prereq = new QuestObjective();
        prereq.setId(prereqId);
        prereq.setQuest(otherQuest); // different quest

        when(objectiveRepository.findById(objId)).thenReturn(Optional.of(obj));
        when(objectiveRepository.findById(prereqId)).thenReturn(Optional.of(prereq));

        assertThatThrownBy(() -> service.addDependency(campaignId, objId, prereqId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same quest");
    }

    @Test
    void removesDependency() {
        UUID objId = UUID.randomUUID();
        UUID prereqId = UUID.randomUUID();
        QuestObjective obj = new QuestObjective();
        obj.setId(objId);
        obj.setQuest(quest);
        when(objectiveRepository.findById(objId)).thenReturn(Optional.of(obj));
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));

        service.removeDependency(campaignId, objId, prereqId);

        verify(dependencyRepository).deleteById(any());
    }

    // ---- Link management ----

    @Test
    void addsLink() {
        UUID targetId = UUID.randomUUID();
        var cmd = new QuestService.QuestLinkCommand(QuestLinkRole.REFERENCE,
                SceneLinkTargetScope.PACKAGE,
                "SCENE", targetId, null, null, "Display", null, 0);
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        when(linkRepository.save(any())).thenAnswer(inv -> {
            QuestLink l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        QuestLink result = service.addLink(campaignId, questId, cmd);

        assertThat(result.getRole()).isEqualTo(QuestLinkRole.REFERENCE);
        assertThat(result.getTargetId()).isEqualTo(targetId);
        assertThat(result.getDisplayText()).isEqualTo("Display");
    }

    @Test
    void updatesLink() {
        UUID linkId = UUID.randomUUID();
        QuestLink link = new QuestLink();
        link.setId(linkId);
        link.setQuest(quest);
        link.setRole(QuestLinkRole.GIVER);
        quest.getLinks().add(link);

        var cmd = new QuestService.QuestLinkCommand(QuestLinkRole.REFERENCE,
                SceneLinkTargetScope.PACKAGE,
                "NPC", UUID.randomUUID(), null, null, "Updated", null, 1);
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        when(linkRepository.save(link)).thenReturn(link);

        QuestLink result = service.updateLink(campaignId, questId, linkId, cmd);

        assertThat(result.getRole()).isEqualTo(QuestLinkRole.REFERENCE);
        assertThat(result.getDisplayText()).isEqualTo("Updated");
        assertThat(result.getSortOrder()).isEqualTo(1);
    }

    @Test
    void deletesLinkAndRenumbers() {
        UUID keepId = UUID.randomUUID();
        UUID delId = UUID.randomUUID();
        QuestLink keep = new QuestLink();
        keep.setId(keepId);
        keep.setSortOrder(0);
        keep.setQuest(quest);
        QuestLink del = new QuestLink();
        del.setId(delId);
        del.setSortOrder(1);
        del.setQuest(quest);
        quest.getLinks().add(keep);
        quest.getLinks().add(del);

        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));

        service.deleteLink(campaignId, questId, delId);

        verify(linkRepository).delete(del);
        assertThat(keep.getSortOrder()).isZero();
    }

    @Test
    void throwsNotFoundOnDeleteLinkFromWrongQuest() {
        UUID linkId = UUID.randomUUID();
        QuestLink link = new QuestLink();
        link.setId(linkId);
        link.setQuest(quest);
        // link belongs to quest but we keep quest's links empty
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));

        assertThatThrownBy(() -> service.deleteLink(campaignId, questId, linkId))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- GIVER link rules ----

    @Test
    void addsGiverLink() {
        UUID targetId = UUID.randomUUID();
        var cmd = giverCmd(targetId);
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        when(linkRepository.save(any())).thenAnswer(inv -> {
            QuestLink l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        QuestLink result = service.addLink(campaignId, questId, cmd);

        assertThat(result.getRole()).isEqualTo(QuestLinkRole.GIVER);
    }

    @Test
    void rejectsSecondGiverLink() {
        UUID firstTarget = UUID.randomUUID();
        UUID secondTarget = UUID.randomUUID();
        QuestLink existing = new QuestLink();
        existing.setId(UUID.randomUUID());
        existing.setQuest(quest);
        existing.setRole(QuestLinkRole.GIVER);
        quest.getLinks().add(existing);

        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));

        var cmd = giverCmd(secondTarget);
        assertThatThrownBy(() -> service.addLink(campaignId, questId, cmd))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replacesGiverLink() {
        UUID oldTarget = UUID.randomUUID();
        UUID newTarget = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        QuestLink existing = new QuestLink();
        existing.setId(existingId);
        existing.setQuest(quest);
        existing.setRole(QuestLinkRole.GIVER);
        existing.setTargetId(oldTarget);
        quest.getLinks().add(existing);

        var cmd = giverCmd(newTarget);
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        when(linkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuestLink result = service.updateLink(campaignId, questId, existingId, cmd);

        assertThat(result.getRole()).isEqualTo(QuestLinkRole.GIVER);
        assertThat(result.getTargetId()).isEqualTo(newTarget);
    }

    @Test
    void clearsGiverByUpdatingRole() {
        UUID existingId = UUID.randomUUID();
        QuestLink existing = new QuestLink();
        existing.setId(existingId);
        existing.setQuest(quest);
        existing.setRole(QuestLinkRole.GIVER);
        quest.getLinks().add(existing);

        var cmd = new QuestService.QuestLinkCommand(QuestLinkRole.REFERENCE,
                SceneLinkTargetScope.PACKAGE,
                "NPC", UUID.randomUUID(), null, null, "New", null, 0);
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        when(linkRepository.save(existing)).thenReturn(existing);

        QuestLink result = service.updateLink(campaignId, questId, existingId, cmd);

        assertThat(result.getRole()).isEqualTo(QuestLinkRole.REFERENCE);
    }

    @Test
    void throwsNotFoundOnCreateQuestForMissingCampaign() {
        var cmd = new QuestService.QuestCommand("X", QuestStatus.NOT_STARTED, null, null, null, null, null, null);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createQuest(campaignId, cmd))
                .isInstanceOf(NotFoundException.class);
    }

    private QuestService.QuestLinkCommand giverCmd(UUID targetId) {
        return new QuestService.QuestLinkCommand(QuestLinkRole.GIVER,
                SceneLinkTargetScope.PACKAGE,
                "NOTE", targetId, null, null, "Giver", null, 0);
    }

    @Test
    void rejectsGiverWithInvalidTargetType() {
        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        var cmd = new QuestService.QuestLinkCommand(QuestLinkRole.GIVER,
                SceneLinkTargetScope.PACKAGE,
                "PARTY_MEMBER", UUID.randomUUID(), null, null, "Bad", null, 0);
        assertThatThrownBy(() -> service.addLink(campaignId, questId, cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOTE or STATBLOCK");
    }

    @Test
    void rejectsUpdateThatCreatesSecondGiver() {
        UUID existingId = UUID.randomUUID();
        QuestLink existing = new QuestLink();
        existing.setId(existingId);
        existing.setQuest(quest);
        existing.setRole(QuestLinkRole.GIVER);
        existing.setTargetType("NOTE");
        quest.getLinks().add(existing);

        UUID otherId = UUID.randomUUID();
        QuestLink other = new QuestLink();
        other.setId(otherId);
        other.setQuest(quest);
        other.setRole(QuestLinkRole.REFERENCE);
        quest.getLinks().add(other);

        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        var cmd = giverCmd(UUID.randomUUID());
        assertThatThrownBy(() -> service.updateLink(campaignId, questId, otherId, cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already has a GIVER");
    }

    @Test
    void rejectsUpdateGiverWithInvalidTargetType() {
        UUID existingId = UUID.randomUUID();
        QuestLink existing = new QuestLink();
        existing.setId(existingId);
        existing.setQuest(quest);
        existing.setRole(QuestLinkRole.GIVER);
        existing.setTargetType("NOTE");
        quest.getLinks().add(existing);

        when(questRepository.findByIdAndCampaignId(questId, campaignId)).thenReturn(Optional.of(quest));
        var cmd = new QuestService.QuestLinkCommand(QuestLinkRole.GIVER,
                SceneLinkTargetScope.PACKAGE,
                "PARTY_MEMBER", UUID.randomUUID(), null, null, "Bad", null, 0);
        assertThatThrownBy(() -> service.updateLink(campaignId, questId, existingId, cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOTE or STATBLOCK");
    }
}
