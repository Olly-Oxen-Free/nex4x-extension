package nex4x.agents.synergy;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import nex4x.agents.AgentType;
import org.apache.log4j.Logger;
import org.json.JSONObject;

/**
 * Cross-type agent synergy modifiers. Example: two COVERT agents on same
 * market give each +10% buildup rate; COVERT + DIPLOMAT boosts Embed action.
 */
public class AgentSynergy {
    private static final Logger log = Global.getLogger(AgentSynergy.class);
    private static JSONObject config;

    private static JSONObject config() {
        if (config == null) {
            try {
                config = Global.getSettings().loadJSON(Nex4xConstants.PATH_AGENT_SYNERGY);
            } catch (Exception e) {
                log.error("[Nex4x] Failed to load agent_synergy.json", e);
                config = new JSONObject();
            }
        }
        return config;
    }

    /** Multiplier applied to buildup rate when other agent types are present on same market. */
    public static float getBuildupMultiplier(AgentType self, int coPresentSameType, int coPresentOtherTypes) {
        JSONObject cfg = config();
        float sameBonus = (float) cfg.optDouble("sameTypeBonusPerAlly", 0.10f);
        float otherBonus = (float) cfg.optDouble("crossTypeBonusPerAlly", 0.05f);
        float cap = (float) cfg.optDouble("synergyCap", 0.50f);
        float total = sameBonus * coPresentSameType + otherBonus * coPresentOtherTypes;
        if (total > cap) total = cap;
        return 1f + total;
    }

    /** Action success bonus when synergistic ally is embedded on same market. */
    public static float getActionSuccessBonus(AgentType self, AgentType ally) {
        if (self == null || ally == null || self == ally) return 0f;
        JSONObject cfg = config();
        JSONObject matrix = cfg.optJSONObject("actionSuccessMatrix");
        if (matrix == null) return 0f;
        JSONObject row = matrix.optJSONObject(self.name());
        if (row == null) return 0f;
        return (float) row.optDouble(ally.name(), 0f);
    }
}
