package nex4x.evaluation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.Logger;

/**
 * Calculates a faction's desperation score (0-100) from strategic position factors.
 * Higher desperation = more willing to make concessions in negotiations.
 *
 * Factors (from spec §5.2.2):
 *  - War weariness: 0 to 30
 *  - Recent market losses: 10 per market lost (last 180 days)
 *  - Fleet pool depletion: 15 or 30
 *  - Multi-front wars: 10 per additional front
 *  - Economy in deficit: 10 (LTV integration)
 *  - Losing war score: 5 per -10 war score (not yet implemented)
 */
public class DesperationCalculator {

    private static final Logger log = Global.getLogger(DesperationCalculator.class);

    private static final float MAX_DESPERATION = 100f;
    private static final float WAR_WEARINESS_MAX_CONTRIBUTION = 30f;
    private static final float MARKET_LOSS_PER = 10f;
    private static final float FLEET_BELOW_30_CONTRIBUTION = 15f;
    private static final float FLEET_BELOW_10_CONTRIBUTION = 30f;
    private static final float MULTI_FRONT_PER = 10f;
    private static final float ECONOMY_DEFICIT_CONTRIBUTION = 10f;

    /**
     * Calculate desperation score for a faction.
     * @return 0-100 score. 0 = confident, 100 = critical.
     */
    public static float calculate(String factionId) {
        float score = 0;

        score += getWarWearinessContribution(factionId);
        score += getFleetPoolContribution(factionId);
        score += getMultiFrontContribution(factionId);
        // Market losses and economy deficit require tracking infrastructure
        // that will be wired in v1a-5 / later versions

        return Math.min(score, MAX_DESPERATION);
    }

    /**
     * War weariness contribution: scales linearly from 0 to 30.
     * Uses Nexerelin's DiplomacyManager.getWarWeariness().
     */
    private static float getWarWearinessContribution(String factionId) {
        try {
            float weariness = exerelin.campaign.DiplomacyManager.getWarWeariness(factionId);
            // Nex weariness is uncapped but typically 0-10000+
            // Map to 0-30: assume 5000 = max contribution
            return Math.min(WAR_WEARINESS_MAX_CONTRIBUTION,
                    (weariness / 5000f) * WAR_WEARINESS_MAX_CONTRIBUTION);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Fleet pool contribution: +15 if below 30% of max, +30 if below 10%.
     */
    private static float getFleetPoolContribution(String factionId) {
        try {
            exerelin.campaign.econ.FleetPoolManager fpm =
                    exerelin.campaign.econ.FleetPoolManager.getManager();
            if (fpm == null) return 0;

            float current = fpm.getCurrentPool(factionId);
            float max = fpm.getMaxPool(factionId);
            if (max <= 0) return 0;

            float ratio = current / max;
            if (ratio < 0.1f) return FLEET_BELOW_10_CONTRIBUTION;
            if (ratio < 0.3f) return FLEET_BELOW_30_CONTRIBUTION;
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Multi-front war contribution: +10 per hostile faction beyond the first.
     */
    private static float getMultiFrontContribution(String factionId) {
        try {
            FactionAPI faction = Global.getSector().getFaction(factionId);
            if (faction == null) return 0;

            int hostileCount = 0;
            for (FactionAPI other : Global.getSector().getAllFactions()) {
                if (other == faction) continue;
                if (other.isNeutralFaction()) continue;
                if (faction.isHostileTo(other)) {
                    // Only count factions that actually own markets (real threats)
                    if (ownsMarkets(other.getId())) {
                        hostileCount++;
                    }
                }
            }

            // First war is "normal" — extra fronts add desperation
            int extraFronts = Math.max(0, hostileCount - 1);
            return extraFronts * MULTI_FRONT_PER;
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean ownsMarkets(String factionId) {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(market.getFactionId())) return true;
        }
        return false;
    }

    // ── Desperation tier helpers ────────────────────────────────

    /** 0-20 = confident. Standard negotiation, no concessions. */
    public static boolean isConfident(float desperation) { return desperation <= 20; }

    /** 21-40 = pressured. Slightly more willing to deal. */
    public static boolean isPressured(float desperation) { return desperation > 20 && desperation <= 40; }

    /** 41-60 = strained. Visibly more flexible. */
    public static boolean isStrained(float desperation) { return desperation > 40 && desperation <= 60; }

    /** 61-80 = desperate. Major concessions even from rigid factions. */
    public static boolean isDesperate(float desperation) { return desperation > 60 && desperation <= 80; }

    /** 81-100 = critical. Will accept almost anything except belief hard-blocks. */
    public static boolean isCritical(float desperation) { return desperation > 80; }

    /**
     * Desperation boosts Federalist and Corporatist voices in internal politics.
     * Returns the dynamic tendency boost for Federalists at this desperation level.
     * Per spec: +2 at 50+, +3 at 80+.
     */
    public static float getFederalistBoost(float desperation) {
        if (desperation >= 80) return 3f;
        if (desperation >= 50) return 2f;
        return 0f;
    }
}
