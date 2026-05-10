package nex4x.leaders;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class LeaderConfigRegistry {
    private static final Logger log = Global.getLogger(LeaderConfigRegistry.class);

    private static final Map<String, LeaderConfig> cache = new HashMap<String, LeaderConfig>();
    private static LeaderConfig defaults;

    public static void load() {
        defaults = loadFrom("data/config/nex4x/leaders/default.json", new LeaderConfig());
        log.info("[Nex4x] LeaderConfigRegistry defaults loaded.");
    }

    public static LeaderConfig get(String factionId) {
        if (defaults == null) {
            // Lazy autoload — protects against UI access before onApplicationLoad-time init.
            load();
        }
        LeaderConfig c = cache.get(factionId);
        if (c == null) {
            String path = "data/config/nex4x/leaders/" + factionId + ".json";
            if (Global.getSettings().fileExistsInCommon(path)) {
                c = loadFrom(path, copyOf(defaults));
            } else {
                c = copyOf(defaults);
            }
            cache.put(factionId, c);
        }
        return c;
    }

    private static LeaderConfig loadFrom(String path, LeaderConfig base) {
        try {
            JSONObject o = Global.getSettings().loadJSON(path);
            base.diplomaticVoiceRank = o.optString("diplomaticVoiceRank", base.diplomaticVoiceRank);
            base.viceroyTitle = o.optString("viceroyTitle", base.viceroyTitle);
            base.leaderTitle = o.optString("leaderTitle", base.leaderTitle);
            base.canSellAiCores = o.optBoolean("canSellAiCores", base.canSellAiCores);
            base.canSellWetwork = o.optBoolean("canSellWetwork", base.canSellWetwork);
            base.noteOnLuddicDenial = o.optBoolean("noteOnLuddicDenial", base.noteOnLuddicDenial);
        } catch (Exception e) {
            log.error("[Nex4x] Failed to load leader config " + path + ": " + e.getMessage());
        }
        return base;
    }

    private static LeaderConfig copyOf(LeaderConfig src) {
        LeaderConfig c = new LeaderConfig();
        if (src != null) {
            c.diplomaticVoiceRank = src.diplomaticVoiceRank;
            c.viceroyTitle = src.viceroyTitle;
            c.leaderTitle = src.leaderTitle;
            c.canSellAiCores = src.canSellAiCores;
            c.canSellWetwork = src.canSellWetwork;
            c.noteOnLuddicDenial = src.noteOnLuddicDenial;
        }
        return c;
    }
}
