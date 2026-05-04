package nex4x.policies;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.Logger;

/**
 * Applies lightweight market-level modifiers derived from active {@link PolicyType}s.
 * Abstract stats (war_desire, negotiation_fatigue, etc.) are consumed directly via
 * {@link PolicyManager#getPolicyModifier} in AI / deal evaluation code paths.
 */
public final class PolicyEffectApplicator {

    private static final Logger log = Global.getLogger(PolicyEffectApplicator.class);
    private static final String STABILITY_MOD_ID = "nex4x_policy_market_stability";

    private PolicyEffectApplicator() {}

    public static void refreshFactionMarkets(String factionId) {
        clearFactionMarkets(factionId);
        float st = PolicyManager.getOrCreate().getPolicyModifier(factionId, "market_stability");
        if (st == 0f) return;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!factionId.equals(m.getFactionId())) continue;
            try {
                m.getStability().modifyFlat(STABILITY_MOD_ID, st, "Nex4x policies");
            } catch (Exception e) {
                log.warn("[Nex4x] policy stability mod: " + e.getMessage());
            }
        }
    }

    public static void clearFactionMarkets(String factionId) {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!factionId.equals(m.getFactionId())) continue;
            try {
                m.getStability().unmodify(STABILITY_MOD_ID);
            } catch (Exception ignore) { }
        }
    }
}
