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

    // Phase 4 side-effect tracking
    private float daysSinceExpiry = 0f;
    private float flatRepApplied = 0f;  // signed: + for friendship, − for denouncement
    private boolean cbUnlocked = false;

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
        if (factionId == null) return false;
        return factionId.equals(declarerFactionId) || factionId.equals(targetFactionId);
    }

    /** Is this between exactly these two factions (in either direction)? */
    public boolean isBetween(String factionA, String factionB) {
        if (factionA == null || factionB == null) return false;
        return (factionA.equals(declarerFactionId) && factionB.equals(targetFactionId))
                || (factionB.equals(declarerFactionId) && factionA.equals(targetFactionId));
    }

    /** Get the other faction in this declaration. */
    public String getOtherFaction(String factionId) {
        if (factionId == null) return null;
        if (factionId.equals(declarerFactionId)) return targetFactionId;
        if (factionId.equals(targetFactionId)) return declarerFactionId;
        return null;
    }

    /** Days remaining. -1 if permanent. */
    public float getDaysRemaining() {
        if (expiryDay < 0) return -1;
        return expiryDay - getCurrentDay();
    }

    public boolean isExpired() {
        // -1 sentinel = permanent; any non-negative expiryDay (including 0) is a real value.
        return expiryDay >= 0 && getCurrentDay() >= expiryDay;
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
    public float getExpiryDay() { return expiryDay; }
    public void setExpiryDay(float d) { this.expiryDay = d; }
    public boolean isActive() { return active && !isExpired(); }
    public void setActive(boolean b) { this.active = b; }

    // Phase 4 side-effect tracking getters/setters
    public float getDaysSinceExpiry() { return daysSinceExpiry; }
    public void advanceExpiredDays(float d) { daysSinceExpiry += d; }
    public float getFlatRepApplied() { return flatRepApplied; }
    public void setFlatRepApplied(float v) { this.flatRepApplied = v; }
    public boolean isCbUnlocked() { return cbUnlocked; }
    public void setCbUnlocked(boolean b) { this.cbUnlocked = b; }

    /**
     * Absolute day count from cycle 206 epoch.
     * Public for use by DeclarationManager and other Phase 4 components.
     */
    public static float currentAbsoluteDay() {
        return Global.getSector().getClock().getDay()
                + (Global.getSector().getClock().getMonth() - 1) * 30f
                + (Global.getSector().getClock().getCycle() - 206) * 365f;
    }

    private static float getCurrentDay() {
        return Global.getSector().getClock().getDay()
                + (Global.getSector().getClock().getMonth() - 1) * 30f
                + (Global.getSector().getClock().getCycle() - 206) * 365f;
    }
}
