package dev.hendrikhoemberg.dmhelper.treasury.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "item_assignment")
public class ItemAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_member_id")
    private PartyMember partyMember; // null = party stash

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "magic_item_id")
    private MagicItem magicItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_item_id")
    private EquipmentItem equipmentItem;

    @Column(length = 500)
    private String customText;

    private int quantity = 1;

    private boolean attuned;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_state", nullable = false, length = 16)
    private InventoryState inventoryState = InventoryState.CARRIED;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Campaign getCampaign() { return campaign; }
    public void setCampaign(Campaign campaign) { this.campaign = campaign; }
    public PartyMember getPartyMember() { return partyMember; }
    public void setPartyMember(PartyMember partyMember) { this.partyMember = partyMember; }
    public MagicItem getMagicItem() { return magicItem; }
    public void setMagicItem(MagicItem magicItem) { this.magicItem = magicItem; }
    public EquipmentItem getEquipmentItem() { return equipmentItem; }
    public void setEquipmentItem(EquipmentItem equipmentItem) { this.equipmentItem = equipmentItem; }
    public String getCustomText() { return customText; }
    public void setCustomText(String customText) { this.customText = customText; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public boolean isAttuned() { return attuned; }
    public void setAttuned(boolean attuned) { this.attuned = attuned; }
    public InventoryState getInventoryState() { return inventoryState; }
    public void setInventoryState(InventoryState inventoryState) { this.inventoryState = inventoryState; }

    public String getItemName() {
        if (magicItem != null) return magicItem.getName();
        if (equipmentItem != null) return equipmentItem.getName();
        return customText;
    }
}
