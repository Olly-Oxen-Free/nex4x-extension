package nex4x.agreements;

import com.fs.starfarer.api.Global;

import java.io.Serializable;

/**
 * A single diplomatic agreement between two factions.
 * Immutable after creation (modifications = cancel old + create new).
 */
public class Agreement implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String factionIdA;
    private final String factionIdB;
    private final AgreementType type;
    private final float creationDay;
    private float expiryDay;  // -1 = permanent
    private boolean active;
    /** True if the deal was struck through a viceroy proxy (restricted negotiation). */
    private boolean viaViceroy;

    public Agreement(String factionIdA, String factionIdB, AgreementType type) {
        this(factionIdA, factionIdB, type, false);
    }

    public Agreement(String factionIdA, String factionIdB, AgreementType type, boolean viaViceroy) {
        // Normalize ordering so lookup is consistent
        if (factionIdA.compareTo(factionIdB) > 0) {
            this.factionIdA = factionIdB;
            this.factionIdB = factionIdA;
        } else {
            this.factionIdA = factionIdA;
            this.factionIdB = factionIdB;
        }
        this.type = type;
        this.creationDay = getCurrentDay();
        this.expiryDay = type.defaultDurationDays > 0
                ? creationDay + type.defaultDurationDays
                : -1;
        this.active = true;
        this.viaViceroy = viaViceroy;
    }

    /** Involves this faction (either side)? */
    public boolean involves(String factionId) {
        return factionIdA.equals(factionId) || factionIdB.equals(factionId);
    }

    /** Get the other faction in the agreement. */
    public String getOtherFaction(String factionId) {
        if (factionIdA.equals(factionId)) return factionIdB;
        if (factionIdB.equals(factionId)) return factionIdA;
        return null;
    }

    /** Days remaining. -1 if permanent. 0 or negative if expired. */
    public float getDaysRemaining() {
        if (expiryDay < 0) return -1;
        return expiryDay - getCurrentDay();
    }

    /** Is this agreement expired? Permanent agreements never expire. */
    public boolean isExpired() {
        return expiryDay > 0 && getCurrentDay() >= expiryDay;
    }

    /** Renew for another full duration from now. */
    public void renew() {
        if (type.defaultDurationDays > 0) {
            this.expiryDay = getCurrentDay() + type.defaultDurationDays;
        }
    }

    /** Cancel this agreement. */
    public void cancel() {
        this.active = false;
    }

    public String getFactionIdA() { return factionIdA; }
    public String getFactionIdB() { return factionIdB; }
    public AgreementType getType() { return type; }
    public float getCreationDay() { return creationDay; }
    public float getExpiryDay() { return expiryDay; }
    public boolean isActive() { return active && !isExpired(); }
    public boolean isViaViceroy() { return viaViceroy; }

    /**
     * Absolute calendar-day index. Note: subtracts cycle 206 to keep the legacy
     * field-value range; preserves save compatibility with existing Agreement timestamps.
     */
    private static float getCurrentDay() {
        return nex4x.util.Nex4xClock.currentAbsoluteDay()
                - 206f * 365f;
    }
}
