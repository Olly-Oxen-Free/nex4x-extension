package nex4x.ai.archetype;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.ai.goals.StrategicGoal;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns all faction CommitmentLedgers. Called daily by Nex4xManager.
 */
public class GrandStrategyManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(GrandStrategyManager.class);

    private final Map<String, CommitmentLedger> ledgers =
            new HashMap<String, CommitmentLedger>();

    private static float CRISIS_MARKET_LOSS_RATIO = 0.5f;
    private static int CRISIS_MULTI_FRONT_WARS = 3;
    private static float CRISIS_STABILITY_THRESHOLD = 3f;

    public static void loadConfig(JSONObject config) {
        CRISIS_MARKET_LOSS_RATIO = (float) config.optDouble("crisisMarketLossRatio", 0.5);
        CRISIS_MULTI_FRONT_WARS = config.optInt("crisisMultiFrontWars", 3);
        CRISIS_STABILITY_THRESHOLD = (float) config.optDouble("crisisStabilityThreshold", 3);
        CommitmentLedger.loadConfig(config);
    }

    public CommitmentLedger getLedger(String factionId) {
        CommitmentLedger ledger = ledgers.get(factionId);
        if (ledger == null) {
            ledger = new CommitmentLedger(factionId);
            ledger.initialize();
            ledgers.put(factionId, ledger);
        }
        return ledger;
    }

    /** Daily advance for a faction. Called after goal manager updates. */
    public void advanceDay(String factionId, List<StrategicGoal> activeGoals,
                           StrategicGoal strategicObjective,
                           float greatestThreatSeverity, Archetype threatArchetype) {
        CommitmentLedger ledger = getLedger(factionId);

        // Check crisis override first
        checkCrisisOverride(factionId, ledger);

        // Normal daily update
        ledger.advanceDay(activeGoals, strategicObjective,
                greatestThreatSeverity, threatArchetype);
    }

    private void checkCrisisOverride(String factionId, CommitmentLedger ledger) {
        if (ledger.isLocked()) return;

        FactionAPI faction = Global.getSector().getFaction(factionId);
        if (faction == null) return;

        List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
        int ownedMarkets = 0;
        float totalStability = 0;
        for (MarketAPI m : markets) {
            if (m.getFaction() == faction) {
                ownedMarkets++;
                totalStability += m.getStabilityValue();
            }
        }

        if (ownedMarkets <= 2) {
            ledger.forceTransition(Archetype.DEFENSIVE_CONSOLIDATION, 60);
            return;
        }

        float avgStability = ownedMarkets > 0 ? totalStability / ownedMarkets : 5;
        if (avgStability < CRISIS_STABILITY_THRESHOLD) {
            ledger.forceTransition(Archetype.DEFENSIVE_CONSOLIDATION, 30);
        }
    }

    public Archetype getArchetype(String factionId) {
        return getLedger(factionId).getCurrentArchetype();
    }
}
