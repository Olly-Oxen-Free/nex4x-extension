package nex4x.agents.actions;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import nex4x.agents.AgentType;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Loads agent_actions.json once and caches configs by action id. */
public class ActionConfigLoader {
    private static final Logger log = Global.getLogger(ActionConfigLoader.class);
    private static Map<String, ActionConfig> configs;

    public static ActionConfig getConfig(String actionId) {
        if (configs == null) load();
        return configs.get(actionId);
    }

    public static Map<String, ActionConfig> getAll() {
        if (configs == null) load();
        return configs;
    }

    private static void load() {
        configs = new HashMap<String, ActionConfig>();
        try {
            JSONObject root = Global.getSettings().loadJSON(Nex4xConstants.PATH_AGENT_ACTIONS);
            Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String id = it.next();
                JSONObject a = root.optJSONObject(id);
                if (a == null) continue;
                AgentType req = AgentType.valueOf(a.optString("requiredType", "COVERT"));
                ActionConfig c = new ActionConfig(
                        id,
                        req,
                        a.optInt("minLevel", 0),
                        a.optLong("creditCost", 0L),
                        (float) a.optDouble("influenceCost", 0f),
                        a.optInt("durationDays", 14),
                        a.optInt("cooldownDays", 30),
                        (float) a.optDouble("relationshipPenaltyOnDetection", -10f)
                );
                configs.put(id, c);
            }
            log.info("[Nex4x] Loaded " + configs.size() + " agent actions");
        } catch (Exception e) {
            log.error("[Nex4x] Failed to load agent_actions.json", e);
        }
    }
}
