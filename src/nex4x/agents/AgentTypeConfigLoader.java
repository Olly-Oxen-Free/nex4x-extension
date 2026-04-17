package nex4x.agents;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Map;

public class AgentTypeConfigLoader {
    private static final Logger log = Global.getLogger(AgentTypeConfigLoader.class);
    private static final Map<AgentType, AgentTypeConfig> configs =
            new EnumMap<AgentType, AgentTypeConfig>(AgentType.class);

    public static void load() {
        try {
            JSONObject json = Global.getSettings().loadJSON(Nex4xConstants.PATH_AGENT_TYPES);
            for (AgentType type : AgentType.values()) {
                String key = type.name();
                if (!json.has(key)) continue;
                JSONObject obj = json.getJSONObject(key);

                String buildupName = obj.getString("buildupName");
                JSONArray levelsArr = obj.getJSONArray("buildupLevels");
                String[] levelNames = new String[levelsArr.length()];
                for (int i = 0; i < levelsArr.length(); i++) {
                    levelNames[i] = levelsArr.getString(i);
                }
                float ratePerDay = (float) obj.getDouble("buildupRatePerDay");
                float decayPerDay = (float) obj.getDouble("buildupDecayPerDay");
                int retrainBase = obj.getInt("retrainBaseDays");
                int retrainPerLevel = obj.getInt("retrainDaysPerLevel");

                int maxPerFaction = obj.optInt("maxPerFactionPerMarket", 0);
                float[] influenceOwner = readFloatArray(obj, "influenceOwner");
                float[] influenceHost = readFloatArray(obj, "influenceHost");
                float[] relationDrift = readFloatArray(obj, "relationDriftPerDay");
                float[] tradeBonus = readFloatArray(obj, "tradeIncomeBonus");

                configs.put(type, new AgentTypeConfig(type, buildupName, levelNames,
                        ratePerDay, decayPerDay, retrainBase, retrainPerLevel,
                        maxPerFaction, influenceOwner, influenceHost,
                        relationDrift, tradeBonus));
            }
            log.info("[Nex4x] Loaded " + configs.size() + " agent type configs");
        } catch (Exception e) {
            log.error("[Nex4x] Failed to load agent_types.json", e);
        }
    }

    public static AgentTypeConfig getConfig(AgentType type) {
        return configs.get(type);
    }

    private static float[] readFloatArray(JSONObject obj, String key) {
        if (!obj.has(key)) return null;
        try {
            JSONArray arr = obj.getJSONArray(key);
            float[] result = new float[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                result[i] = (float) arr.getDouble(i);
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}
