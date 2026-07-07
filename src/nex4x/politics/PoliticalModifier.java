package nex4x.politics;

import nex4x.data.TendencyId;

import java.io.Serializable;

/**
 * A single dynamic modifier on a faction's tendency profile.
 * Decays over time. Multiple modifiers can stack on the same tendency.
 */
public class PoliticalModifier implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ModifierEventType eventType;
    private final TendencyId targetTendency;
    private float amount;
    private final float decayPerDay;
    private final float createdDay;
    private final String details;

    public PoliticalModifier(ModifierEventType eventType, float amount,
                             float createdDay, String details) {
        this.eventType = eventType;
        this.targetTendency = eventType.targetTendency;
        this.amount = amount;
        this.decayPerDay = eventType.decayPerDay;
        this.createdDay = createdDay;
        this.details = details;
    }

    public ModifierEventType getEventType() { return eventType; }
    public TendencyId getTargetTendency() { return targetTendency; }
    public float getAmount() { return amount; }
    public String getDetails() { return details; }
    public float getCreatedDay() { return createdDay; }

    /**
     * Decay magnitude toward zero, preserving sign. Returns true when the modifier has
     * decayed to zero (or crossed it) and should be pruned.
     */
    public boolean decay() {
        if (amount > 0f) {
            amount -= decayPerDay;
            if (amount < 0f) amount = 0f;
        } else if (amount < 0f) {
            amount += decayPerDay;
            if (amount > 0f) amount = 0f;
        }
        return amount == 0f;
    }

    public boolean isExpired() { return amount == 0f; }
}
