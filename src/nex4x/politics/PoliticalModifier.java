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

    /** Decay this modifier. Returns true if it should be pruned. */
    public boolean decay() {
        amount -= decayPerDay;
        return amount <= 0;
    }

    public boolean isExpired() { return amount <= 0; }
}
