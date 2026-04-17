package nex4x.declarations;

import com.fs.starfarer.api.Global;

import java.io.Serializable;

/**
 * A single active public declaration between two factions.
 * Similar structure to Agreement but with declaration-specific semantics.
 */
public class Declaration implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String declarerFactionId;
    private final String targetFactionId;
    private final DeclarationType type;
    private final float creationDay;
    private float expiryDay;  // -1 = permanent
    private boolean active;

    public Declaration(String declarerFactionId, String targetFactionId,
                       DeclarationType type) {
        this.declarerFactionId = declarerFactionId;
        this.targetFactionId = targetFactionId;
        this.type = type;
        this.creationDay = getCurrentDay();
        this.expiryDay = type.defaultDurationDays > 0
                ? creationDay + type.defaultDurationDays
                : -1;
        this.active = true;
    }

    /** Does this declaration involve the given faction (either side)? */
    public boolean involves(String factionId) {
        return declarerFactionId.equals(factionId) || targetFactionId.equals(factionId);
    }

    /** Is this between exactly these two factions (in either direction)? */
    public boolean isBetween(String factionA, String factionB) {
        return (declarerFactionId.equals(factionA) && targetFactionId.equals(factionB))
                || (declarerFactionId.equals(factionB) && targetFactionId.equals(factionA));
    }

    /** Get the other faction in this declaration. */
    public String getOtherFaction(String factionId) {
        if (declarerFactionId.equals(factionId)) return targetFactionId;
        if (targetFactionId.equals(factionId)) return declarerFactionId;
        return null;
    }

    /** Days remaining. -1 if permanent. */
    public float getDaysRemaining() {
        if (expiryDay < 0) return -1;
        return expiryDay - getCurrentDay();
    }

    public boolean isExpired() {
        return expiryDay > 0 && getCurrentDay() >= expiryDay;
    }

    /** Withdraw / cancel this declaration. */
    public void withdraw() {
        this.active = false;
    }

    // ── Getters ────────────────────────────────────────────────

    public String getDeclarerFactionId() { return declarerFactionId; }
    public String getTargetFactionId() { return targetFactionId; }
    public DeclarationType getType() { return type; }
    public float getCreationDay() { return creationDay; }
    public boolean isActive() { return active && !isExpired(); }

    private static float getCurrentDay() {
        return Global.getSector().getClock().getDay()
                + (Global.getSector().getClock().getMonth() - 1) * 30f
                + (Global.getSector().getClock().getCycle() - 206) * 365f;
    }
}
