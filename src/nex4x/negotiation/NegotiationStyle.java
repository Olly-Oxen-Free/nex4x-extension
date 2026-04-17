package nex4x.negotiation;

import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;

/**
 * Faction negotiation styles derived from political tendencies (spec §5.2.2).
 *
 * Rigid:       Opens with exact terms. No adjustment. Take-it-or-leave-it. (1 round)
 * Anchoring:   Opens inflated (120-150%). Expects haggling. (2-3 rounds)
 * Cooperative: Opens near fair value. Makes concessions. (3-4 rounds)
 * Erratic:     Unpredictable demands. Random factor on evaluations. (2 rounds)
 */
public enum NegotiationStyle {
    RIGID("Rigid", 1, 1.0f, 0f),
    ANCHORING("Anchoring", 3, 1.35f, 0.7f),
    COOPERATIVE("Cooperative", 4, 1.05f, 0.9f),
    ERRATIC("Erratic", 2, 1.0f, 0.5f);

    public final String displayName;
    /** Maximum negotiation rounds before final take-it-or-leave-it. */
    public final int maxRounds;
    /** Opening ask multiplier (1.35 = asks 35% more than real target). */
    public final float openingAskMultiplier;
    /**
     * Minimum fraction of real target the faction will accept in a counter.
     * E.g., Anchoring at 0.7 means they'll accept down to 70% of their actual target.
     * Rigid at 0.0 means they won't accept anything below their opening ask.
     */
    public final float minimumAcceptFraction;

    NegotiationStyle(String displayName, int maxRounds,
                     float openingAskMultiplier, float minimumAcceptFraction) {
        this.displayName = displayName;
        this.maxRounds = maxRounds;
        this.openingAskMultiplier = openingAskMultiplier;
        this.minimumAcceptFraction = minimumAcceptFraction;
    }

    /** Will this style generate counter-offers on rejection? */
    public boolean willCounterOffer() {
        return this != RIGID;
    }

    /**
     * Likelihood of counter-offering (0-1) instead of flat rejection.
     * Anchoring: 0.8 (they want to make deals).
     * Cooperative: 0.6 (willing but not aggressive).
     * Erratic: 0.4 (coin flip).
     * Rigid: 0 (never counter).
     */
    public float getCounterOfferChance() {
        switch (this) {
            case ANCHORING: return 0.8f;
            case COOPERATIVE: return 0.6f;
            case ERRATIC: return 0.4f;
            default: return 0f;
        }
    }

    /**
     * Derive negotiation style from a faction's political tendencies.
     * Dominant tendency determines the style.
     */
    public static NegotiationStyle deriveFromProfile(String factionId) {
        TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
        if (profile == null) return COOPERATIVE;

        TendencyId dominant = profile.getDominant();
        if (dominant == null) return ERRATIC;

        // Check if dominant tendency is strong enough to set style clearly
        float total = profile.getTotal();
        float dominantWeight = profile.getWeight(dominant);
        float dominantFraction = total > 0 ? dominantWeight / total : 0;

        // If no tendency dominates (< 25% share), faction is mixed → Erratic
        if (dominantFraction < 0.25f) return ERRATIC;

        switch (dominant) {
            case MILITARISTS:
            case ZEALOTS:
                return RIGID;
            case CORPORATISTS:
                return ANCHORING;
            case FEDERALISTS:
            case ECOLOGISTS:
                return COOPERATIVE;
            case INDUSTRIALISTS:
                // Industrialists are pragmatic — Cooperative if Federalist secondary, Anchoring otherwise
                float fedWeight = profile.getWeight(TendencyId.FEDERALISTS);
                float corWeight = profile.getWeight(TendencyId.CORPORATISTS);
                return fedWeight > corWeight ? COOPERATIVE : ANCHORING;
            default:
                return COOPERATIVE;
        }
    }
}
