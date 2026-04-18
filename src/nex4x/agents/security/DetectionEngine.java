package nex4x.agents.security;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.Nex4xConstants;
import nex4x.agents.AgentType;
import nex4x.agents.BuildupLevel;
import nex4x.agents.BuildupTracker;
import org.apache.log4j.Logger;
import org.json.JSONObject;

/** Computes per-market detection strength and per-action detection rolls. */
public class DetectionEngine {
    private static final Logger log = Global.getLogger(DetectionEngine.class);

    private static JSONObject config;

    private static JSONObject config() {
        if (config == null) {
            try {
                config = Global.getSettings().loadJSON(Nex4xConstants.PATH_MARKET_SECURITY);
            } catch (Exception e) {
                log.error("[Nex4x] Failed to load market_security.json", e);
                config = new JSONObject();
            }
        }
        return config;
    }

    public static float getMarketDetection(MarketAPI market) {
        if (market == null) return 0f;
        JSONObject cfg = config();
        MarketSecurityData d = new MarketSecurityData();

        float baseBySize = (float) cfg.optDouble("detectionPerSize", 2.0f) * market.getSize();
        float stability = (float) cfg.optDouble("detectionPerStabilityPoint", 0.5f) * market.getStabilityValue();
        d.setBaseDetection(baseBySize + stability);

        if (market.hasIndustry(Nex4xConstants.BUILDING_CYBER_SECURITY)) {
            d.setCyberBonus((float) cfg.optDouble("cyberSecurityBonus", 15.0f));
        }
        if (market.hasIndustry(Nex4xConstants.BUILDING_DIPLOMATIC_EMBASSY)) {
            d.setEmbassyAssistBonus((float) cfg.optDouble("embassyAssistBonus", 3.0f));
        }
        if (market.hasIndustry(Nex4xConstants.BUILDING_INTELLIGENCE_BUREAU)) {
            d.setPatrolAssistBonus((float) cfg.optDouble("intelBureauBonus", 8.0f));
        }
        return d.getTotal();
    }

    /** Attacker strength = base + buildup level bonus. */
    public static float getAttackerStrength(AgentType type, BuildupTracker tracker) {
        JSONObject cfg = config();
        int level = tracker == null ? BuildupLevel.FRESH : tracker.getLevel();
        float base = (float) cfg.optDouble("attackerBase", 10f);
        float perLevel = (float) cfg.optDouble("attackerPerLevel", 5f);
        return base + perLevel * level;
    }

    /** Detection probability: 0..1 — defender vs attacker sigmoid. */
    public static float detectionChance(float attacker, float defender) {
        float diff = defender - attacker;
        float p = 1f / (1f + (float) Math.exp(-diff / 10f));
        return p;
    }

    public static boolean rollDetection(float attacker, float defender) {
        return Math.random() < detectionChance(attacker, defender);
    }
}
