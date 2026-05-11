package nex4x.leaders;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A pool of dialogue lines keyed by (Situation, ReputationTier).
 * Loaded from JSON of the shape:
 *   { "greeting": { "NEUTRAL": ["line 1", "line 2"], "COOPERATIVE": [...] } }
 */
public class DialoguePool {
    private static final Logger log = Global.getLogger(DialoguePool.class);

    private final Map<Situation, EnumMap<ReputationTier, List<String>>> data =
            new HashMap<Situation, EnumMap<ReputationTier, List<String>>>();

    public List<String> get(Situation situation, ReputationTier tier) {
        EnumMap<ReputationTier, List<String>> inner = data.get(situation);
        if (inner == null) return Collections.emptyList();
        List<String> lines = inner.get(tier);
        return lines == null ? Collections.<String>emptyList() : lines;
    }

    public static DialoguePool loadFromPath(String modFilePath) {
        DialoguePool pool = new DialoguePool();
        try {
            JSONObject root = Global.getSettings().loadJSON(modFilePath);
            Iterator<String> sitKeys = root.keys();
            while (sitKeys.hasNext()) {
                String sitKey = sitKeys.next();
                Situation situation = Situation.fromJsonKey(sitKey);
                if (situation == null) {
                    log.warn("[Nex4x] Unknown situation key '" + sitKey + "' in " + modFilePath);
                    continue;
                }
                JSONObject tierBlock = root.getJSONObject(sitKey);
                EnumMap<ReputationTier, List<String>> inner =
                        new EnumMap<ReputationTier, List<String>>(ReputationTier.class);
                Iterator<String> tierKeys = tierBlock.keys();
                while (tierKeys.hasNext()) {
                    String tierKey = tierKeys.next();
                    ReputationTier tier;
                    try { tier = ReputationTier.valueOf(tierKey.toUpperCase()); }
                    catch (IllegalArgumentException e) {
                        log.warn("[Nex4x] Unknown tier key '" + tierKey + "' in " + modFilePath);
                        continue;
                    }
                    JSONArray arr = tierBlock.getJSONArray(tierKey);
                    List<String> lines = new ArrayList<String>(arr.length());
                    for (int i = 0; i < arr.length(); i++) lines.add(arr.getString(i));
                    inner.put(tier, lines);
                }
                pool.data.put(situation, inner);
            }
        } catch (Exception e) {
            log.error("[Nex4x] Failed to load DialoguePool from " + modFilePath + ": " + e.getMessage());
        }
        return pool;
    }
}
