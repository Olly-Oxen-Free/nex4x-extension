package nex4x.negotiation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import nex4x.leaders.LeaderProfile;
import nex4x.leaders.Personality;
import nex4x.leaders.ReputationTier;

/**
 * v5 placeholder valuation. v6 replaces scarcityMod with real market-production lookup.
 * All multipliers are centered on 1.0; the formula is multiplicative so unknown
 * axes (stub scarcity) are cheap no-ops (1.0 × anything = anything).
 */
public class Valuator {

    /**
     * Value of an item from the leader's (receiver's) perspective.
     * Caller supplies baseCreditValue (already computed from CommoditySpec etc.).
     */
    public static int valueTo(LeaderProfile leader, String proposerFactionId,
                              ItemCategory category, int baseCreditValue,
                              String itemId) {
        float mod = 1.0f
                * personalityWeight(leader.getPersonality(), category)
                * goalAlignment(leader, category, itemId)
                * relationScalar(leader.getFactionId(), proposerFactionId)
                * scarcityMod(leader.getFactionId(), itemId);
        return Math.round(baseCreditValue * mod);
    }

    /** Placeholder — real impl in v6 reads market stockpile + monthly demand. */
    public static float scarcityMod(String leaderFactionId, String itemId) {
        return 1.0f;
    }

    public static float relationScalar(String leaderFactionId, String proposerFactionId) {
        FactionAPI lf = Global.getSector().getFaction(leaderFactionId);
        if (lf == null) return 1.0f;
        float rel = lf.getRelationship(proposerFactionId);
        ReputationTier tier = ReputationTier.fromRelation(rel);
        switch (tier) {
            case HOSTILE:     return 0.6f;
            case SUSPICIOUS:  return 0.8f;
            case NEUTRAL:     return 1.0f;
            case FAVORABLE:   return 1.1f;
            case COOPERATIVE: return 1.2f;
            default:          return 1.0f;
        }
    }

    public static float personalityWeight(Personality p, ItemCategory cat) {
        if (p == null || cat == null) return 1.0f;
        switch (p) {
            case MERCANTILE:
                if (cat == ItemCategory.TRADE_PACT)  return 1.25f;
                if (cat == ItemCategory.COMMODITY)   return 1.10f;
                if (cat == ItemCategory.CREDITS)     return 1.05f;
                return 1.0f;
            case ZEALOUS:
                if (cat == ItemCategory.ALLIANCE)    return 1.50f; // heavily weighted, belief-filtered elsewhere
                return 0.9f;
            case HAUGHTY:
                if (cat == ItemCategory.TRIBUTE)     return 1.30f;
                return 1.10f; // +10% threshold baked here
            case RUTHLESS:
                if (cat == ItemCategory.TRIBUTE)     return 1.50f;
                if (cat == ItemCategory.SPARE_CONCESSION) return 1.25f; // +25% tax on "spare" deals
                return 1.0f;
            case PARANOID:
                if (cat == ItemCategory.INTEL)       return 2.0f;  // doubles secret-knowledge cost
                return 1.0f;
            case GENIAL:
                return 0.95f;  // -5% threshold baked in
            case CAPRICIOUS:
                // +-15% noise -- deterministic per (faction, item) seed to avoid re-roll abuse
                return 1.0f + noise(p, cat);
            case PRAGMATIC:
            default:
                return 1.0f;
        }
    }

    static float noise(Personality p, ItemCategory cat) {
        long seed = (long) p.hashCode() * 31 + cat.hashCode();
        java.util.Random r = new java.util.Random(seed);
        return (r.nextFloat() - 0.5f) * 0.30f; // +-15%
    }

    public static float goalAlignment(LeaderProfile leader, ItemCategory cat, String itemId) {
        // v5 stub -- full StrategicGoal alignment wiring needs a getGoalManager lookup;
        // keep a permissive default so the layer is active but not disruptive.
        // (v5.1: read Nex4xManager.getGoalManager(leader.factionId).currentGoals())
        return 1.0f;
    }
}
