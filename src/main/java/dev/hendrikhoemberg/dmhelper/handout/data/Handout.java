package dev.hendrikhoemberg.dmhelper.handout.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "handout", indexes = {
    @Index(name = "idx_handout_campaign", columnList = "campaign_id")
})
public class Handout {

    public enum AssetKind {
        PLAYER_HANDOUT, DM_REFERENCE, REGIONAL_MAP, TACTICAL_MAP, ILLUSTRATION, SOURCE_PAGE;

        public boolean isMapLike() {
            return this == REGIONAL_MAP || this == TACTICAL_MAP;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 500)
    private String fileName;

    @Column(length = 100)
    private String contentType;

    @Column(columnDefinition = "CLOB")
    private String tags;

    @Column(nullable = false)
    private boolean dmOnly = true;

    @Column(nullable = false)
    private boolean presented = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_kind", nullable = false, length = 24)
    private AssetKind assetKind = AssetKind.SOURCE_PAGE;

    public AssetKind getAssetKind() { return assetKind; }
    public void setAssetKind(AssetKind assetKind) {
        this.assetKind = java.util.Objects.requireNonNull(assetKind, "asset kind");
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public boolean isDmOnly() { return dmOnly; }
    public void setDmOnly(boolean dmOnly) { this.dmOnly = dmOnly; }

    public boolean isPresented() { return presented; }
    public void setPresented(boolean presented) { this.presented = presented; }

}
