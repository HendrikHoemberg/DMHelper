package dev.hendrikhoemberg.dmhelper.session.data;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "session_scene_visit", uniqueConstraints =
        @UniqueConstraint(name = "uq_session_scene_visit", columnNames = {"session_id", "scene_id"}))
public class SessionSceneVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private CampaignSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(nullable = false, updatable = false)
    private Instant visitedAt;

    private Instant completedAt;
}
