package nex4x.ai.archetype;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Optional per-faction forced starting archetype from {@code archetype_overrides.json}.
 */
public final class ArchetypeOverrideRegistry {

    private static final Logger log = Global.getLogger(ArchetypeOverrideRegistry.class);

    private static final Map<String, Archetype> forced = new HashMap<String, Archetype>();

    private ArchetypeOverrideRegistry() {}

    public static void load() {
        forced.clear();
        try {
            JSONObject root = Global.getSettings().getMergedJSONForMod(
                    Nex4xConstants.PATH_ARCHETYPE_OVERRIDES, Nex4xConstants.MOD_ID);
            JSONObject map = root.optJSONObject("overrides");
            if (map == null) return;
            for (java.util.Iterator<?> keys = map.keys(); keys.hasNext(); ) {
                String fid = (String) keys.next();
                String val = map.optString(fid, null);
                if (val == null || val.isEmpty()) continue;
                try {
                    forced.put(fid.toLowerCase(), Archetype.valueOf(val.trim()));
                } catch (IllegalArgumentException e) {
                    log.warn("[Nex4x] Unknown archetype override for " + fid + ": " + val);
                }
            }
            log.info("[Nex4x] Loaded " + forced.size() + " archetype override(s)");
        } catch (Exception e) {
            log.info("[Nex4x] No archetype overrides (optional): " + e.getMessage());
        }
    }

    public static Archetype getForced(String factionId) {
        if (factionId == null) return null;
        return forced.get(factionId.toLowerCase());
    }
}
