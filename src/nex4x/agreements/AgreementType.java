package nex4x.agreements;

import java.awt.Color;

/**
 * The agreement ladder — 5 tiers on the alliance track, plus a parallel trade track.
 * Factions must progress through lower tiers to reach higher ones.
 * Trade agreements are independent and can coexist with any alliance tier.
 */
public enum AgreementType {
    // Alliance track (tier 0-4)
    COLD_WAR(0, "Cold War", "No agreement. Factions can attack freely.",
            new Color(128, 128, 128), -1, Float.MIN_VALUE),
    NAP(1, "Non-Aggression Pact", "Neither party can declare war. Breaking = Oathbreaker badge.",
            new Color(100, 200, 100), 120, 10f),
    DEFENSIVE_PACT(2, "Defensive Pact", "If one party is attacked, the other joins the war.",
            new Color(50, 150, 255), 180, 20f),
    MILITARY_PARTNERSHIP(3, "Military Partnership", "Joint war proposals. Coordinated invasions. Fleet support.",
            new Color(200, 150, 50), 240, 40f),
    ECONOMIC_PARTNERSHIP(3, "Economic Partnership", "Deep trade integration via LTV. Shared market access.",
            new Color(50, 200, 50), 240, 40f),
    COALITION(4, "Full Coalition", "Effectively one polity to outsiders. Shared war/peace decisions.",
            new Color(255, 200, 50), -1, 60f),

    // Parallel trade track (independent of alliance tier)
    TRADE_AGREEMENT(-1, "Trade Agreement", "Opens mercantile convoy routes. Both parties get economic bonus.",
            new Color(150, 200, 100), 90, -10f);

    /** Alliance ladder tier. -1 for parallel track agreements. */
    public final int tier;
    public final String displayName;
    public final String description;
    public final Color color;
    /** Default duration in days. -1 = permanent until dissolved. */
    public final float defaultDurationDays;
    /**
     * Minimum {@link com.fs.starfarer.api.campaign.FactionAPI#getRelationship(String)} value
     * (roughly -100..+100) for AI tier-upgrade proposals; compared directly, not divided.
     */
    public final float relationThreshold;

    AgreementType(int tier, String displayName, String description,
                  Color color, float defaultDurationDays, float relationThreshold) {
        this.tier = tier;
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.defaultDurationDays = defaultDurationDays;
        this.relationThreshold = relationThreshold;
    }

    /** Is this on the alliance ladder (not a parallel track)? */
    public boolean isAllianceTrack() { return tier >= 0; }

    /** Can this agreement be proposed given the current alliance tier? */
    public boolean canProposeFrom(AgreementType currentTier) {
        if (!isAllianceTrack()) return true; // parallel track — always proposable if relations met
        if (!currentTier.isAllianceTrack()) return false;
        // Must be exactly one tier above current
        return this.tier == currentTier.tier + 1;
    }

    /** Higher-tier alliance outweighs lower in conflicts. */
    public boolean outweighs(AgreementType other) {
        if (!isAllianceTrack() || !other.isAllianceTrack()) return false;
        return this.tier > other.tier;
    }

    /** Get the next tier up on the alliance ladder, or null if at top. */
    public AgreementType getNextAllianceTier() {
        switch (this) {
            case COLD_WAR: return NAP;
            case NAP: return DEFENSIVE_PACT;
            case DEFENSIVE_PACT: return MILITARY_PARTNERSHIP; // default to military; economic is alternate
            case MILITARY_PARTNERSHIP: return COALITION;
            case ECONOMIC_PARTNERSHIP: return COALITION;
            default: return null;
        }
    }
}
