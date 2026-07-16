package dev.hendrikhoemberg.dmhelper.calendar.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TimelineEventRepository extends JpaRepository<TimelineEvent, UUID> {
    List<TimelineEvent> findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(UUID campaignId);

    List<TimelineEvent> findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAscIdAsc(UUID campaignId);
}
