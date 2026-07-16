package dev.hendrikhoemberg.dmhelper.session.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CampaignSessionRepository extends JpaRepository<CampaignSession, UUID> {

    Optional<CampaignSession> findByCampaignId(UUID campaignId);

    Optional<CampaignSession> findFirstByStatusNotOrderByUpdatedAtDesc(CampaignSession.Status status);
}
