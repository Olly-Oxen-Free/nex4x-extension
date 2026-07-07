package nex4x.negotiation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import nex4x.leaders.LeaderProfile;
import nex4x.leaders.Personality;
import nex4x.leaders.ReputationTier;

/**
 * Multiplicative valuation pipeline. Centred on 1.0 so any single axis returning 1.0
 * leaves the value unchanged. Order: personality × goal-alignment × relation × scarcity.
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

    /**
     * Scarcity multiplier from leader-faction's economy state.
     * Higher when the commodity is undersupplied (demand &gt; available) — they value it more.
     * Returns 1.0 if itemId is not a commodity, market data missing, or computation throws.
     */
    public static float scarcityMod(String leaderFactionId, String itemId) {
        if (itemId == null || leaderFactionId == null) return 1.0f;
        try {
            com.fs.starfarer.api.campaign.econ.CommoditySpecAPI spec =
                    Global.getSettings().getCommoditySpec(itemId);
            if (spec == null) return 1.0f;
            float demand = 0f;
            float available = 0f;
            for (com.fs.starfarer.api.campaign.econ.MarketAPI m
                    : Global.getSector().getEconomy().getMarketsCopy()) {
                if (!leaderFactionId.equals(m.getFactionId())) continue;
                if (m.isHidden()) continue;
                com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI com = m.getCommodityData(itemId);
                if (com == null) continue;
                demand += com.getMaxDemand();
                available += com.getAvailable();
            }
            if (demand <= 0f) return 1.0f;
            // ratio < 1 = shortage (raise value), > 1 = surplus (lower value).
            float ratio = available / demand;
            // Clamp to ~[0.7, 1.5]: don't let extreme markets dominate price.
            if (ratio < 0.5f) return 1.5f;
            if (ratio < 1.0f) return 1.0f + (1.0f - ratio) * 0.5f;
            if (ratio > 2.0f) return 0.7f;
            if (ratio > 1.0f) return 1.0f - (ratio - 1.0f) * 0.3f;
            return 1.0f;
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    public static float relationScalar(String leaderFactionId, String proposerFactionId) {
        FactionAPI lf = Global.getSector().getFaction(leaderFactionId);
        if (lf == null) return 1.0f;
        float rel = lf.getRelationship(proposerFactionId);
        ReputationTier tier = ReputationTier.fromRawRelation(rel);
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

    /**
     * Multiplier reflecting how the deal item aligns with the leader's active strategic goals.
     * If a goal targets the proposer, deals matter more; alliance deals while a goal pushes
     * for alliance-building are more valuable. Returns 1.0 (neutral) if no manager wired.
     */
    public static float goalAlignment(LeaderProfile leader, ItemCategory cat, String itemId) {
        if (leader == null || cat == null) return 1.0f;
        try {
            nex4x.managers.Nex4xManager mgr = nex4x.managers.Nex4xManager.getManager();
            if (mgr == null) return 1.0f;
            nex4x.ai.StrategicGoalManager gm = mgr.getGoalManager(leader.getFactionId());
            if (gm == null) return 1.0f;
            java.util.List<nex4x.ai.goals.StrategicGoal> active = gm.getActiveGoals();
            if (active == null || active.isEmpty()) return 1.0f;
            float maxMult = 1.0f;
            for (nex4x.ai.goals.StrategicGoal g : active) {
                if (g == null || g.type == null) continue;
                float m = matchMult(g.type, cat);
                if (m > maxMult) maxMult = m;
            }
            return maxMult;
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    private static float matchMult(nex4x.ai.goals.GoalType gt, ItemCategory cat) {
        switch (gt) {
            case BUILD_ALLIANCE:
            case IMPROVE_RELATIONS:
                return cat == ItemCategory.ALLIANCE ? 1.30f
                     : cat == ItemCategory.TRADE_PACT ? 1.15f : 1.0f;
            case ECONOMIC_DOMINANCE:
                return cat == ItemCategory.TRADE_PACT ? 1.25f
                     : cat == ItemCategory.COMMODITY ? 1.10f : 1.0f;
            case CONTAIN_RIVAL:
            case END_WAR:
                return cat == ItemCategory.TRIBUTE ? 1.20f
                     : cat == ItemCategory.INTEL ? 1.15f : 1.0f;
            case SPREAD_IDEOLOGY:
                return cat == ItemCategory.INTEL ? 1.20f : 1.0f;
            default:
                return 1.0f;
        }
    }
}
