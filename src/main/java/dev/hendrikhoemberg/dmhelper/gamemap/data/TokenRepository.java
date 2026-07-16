package dev.hendrikhoemberg.dmhelper.gamemap.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TokenRepository extends JpaRepository<Token, UUID> {
    List<Token> findByMapIdOrderByNameAsc(UUID mapId);
    List<Token> findByPartyMemberId(UUID partyMemberId);
    void deleteByMapId(UUID mapId);
}
