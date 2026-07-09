package dev.hendrikhoemberg.dmhelper.dice.data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dice_roll")
public class DiceRoll {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String expression;

    @Column(columnDefinition = "CLOB")
    private String rolls;

    private int modifier;

    @Column(nullable = false)
    private int total;

    private boolean advantage;

    private boolean disadvantage;

    @Column(length = 36)
    private String encounterId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getRolls() { return rolls; }
    public void setRolls(String rolls) { this.rolls = rolls; }

    public int getModifier() { return modifier; }
    public void setModifier(int modifier) { this.modifier = modifier; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public boolean isAdvantage() { return advantage; }
    public void setAdvantage(boolean advantage) { this.advantage = advantage; }

    public boolean isDisadvantage() { return disadvantage; }
    public void setDisadvantage(boolean disadvantage) { this.disadvantage = disadvantage; }

    public String getEncounterId() { return encounterId; }
    public void setEncounterId(String encounterId) { this.encounterId = encounterId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
