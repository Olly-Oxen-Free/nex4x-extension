package nex4x.leaders;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Loads {@code data/config/nex4x/leader_access.json} once per application.
 */
public final class LeaderAccessConfig {

    private static final Logger log = Global.getLogger(LeaderAccessConfig.class);

    private static float rapportThreshold = 0.50f;
    private static int ownMarketCount = 1;
    private static boolean commissionMustMatchTarget = true;
    private static final Map<String, Float> rapportOverrides = new HashMap<String, Float>();

    private LeaderAccessConfig() {}

    public static void load() {
        try {
            JSONObject root = Global.getSettings().loadJSON("data/config/nex4x/leader_access.json");
            rapportThreshold = (float) root.optDouble("rapportThreshold", 0.50);
            ownMarketCount = root.optInt("ownMarketCount", 1);
            commissionMustMatchTarget = root.optBoolean("commissionMustMatchTarget", true);
            rapportOverrides.clear();
            JSONObject ov = root.optJSONObject("overrides");
            if (ov != null) {
                for (Iterator<?> it = ov.keys(); it.hasNext(); ) {
                    String fid = (String) it.next();
                    JSONObject row = ov.optJSONObject(fid);
                    if (row != null && row.has("rapportThreshold")) {
                        rapportOverrides.put(fid, (float) row.optDouble("rapportThreshold", rapportThreshold));
                    }
                }
            }
            log.info("[Nex4x] leader_access.json loaded (rapport>=" + rapportThreshold + ")");
        } catch (Exception e) {
            log.warn("[Nex4x] leader_access.json load failed, using defaults: " + e.getMessage());
        }
    }

    public static float getRapportThreshold(String targetFactionId) {
        Float o = rapportOverrides.get(targetFactionId);
        return o != null ? o : rapportThreshold;
    }

    public static int getOwnMarketCount() { return ownMarketCount; }

    public static boolean isCommissionMustMatchTarget() {
        return commissionMustMatchTarget;
    }
}
