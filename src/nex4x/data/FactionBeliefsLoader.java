package nex4x.data;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FactionBeliefsLoader {

    private static final Logger log = Global.getLogger(FactionBeliefsLoader.class);
    private static final Map<String, FactionBeliefs> factionBeliefs = new HashMap<String, FactionBeliefs>();

    private static final String[] VANILLA_FACTIONS = {
        "hegemony", "tritachyon", "luddic_church", "luddic_path",
        "persean", "sindrian_diktat", "pirates", "independent",
        "knights_of_ludd", "lions_guard"
    };

    public static void load() throws Exception {
        factionBeliefs.clear();

        for (String factionId : VANILLA_FACTIONS) {
            loadFactionBeliefs(factionId);
        }
    }

    private static void loadFactionBeliefs(String factionId) {
        String path = Nex4xConstants.PATH_FACTION_BELIEFS_DIR + factionId + ".json";
        try {
            JSONObject json = Global.getSettings().getMergedJSONForMod(path, Nex4xConstants.MOD_ID);
            JSONArray beliefsArray = json.getJSONArray("beliefs");

            List<FactionBeliefs.BeliefEntry> entries = new ArrayList<FactionBeliefs.BeliefEntry>();
            for (int i = 0; i < beliefsArray.length(); i++) {
                JSONObject entry = beliefsArray.getJSONObject(i);
                String beliefId = entry.getString("id");
                int strength = entry.getInt("strength");
                String visibility = entry.optString("visibility", "public");

                if (BeliefRegistry.get(beliefId) == null) {
                    log.warn("[Nex4x] Faction " + factionId + " references unknown belief: " + beliefId);
                    continue;
                }

                entries.add(new FactionBeliefs.BeliefEntry(beliefId, strength, visibility));
            }

            factionBeliefs.put(factionId, new FactionBeliefs(factionId, entries));
            log.info("[Nex4x] Loaded " + entries.size() + " beliefs for " + factionId);

        } catch (Exception e) {
            log.info("[Nex4x] No belief file for " + factionId + " — will have empty beliefs");
        }
    }

    public static FactionBeliefs getBeliefs(String factionId) {
        FactionBeliefs beliefs = factionBeliefs.get(factionId);
        if (beliefs == null) {
            beliefs = new FactionBeliefs(factionId, new ArrayList<FactionBeliefs.BeliefEntry>());
        }
        return beliefs;
    }

    public static Map<String, FactionBeliefs> getAll() {
        return factionBeliefs;
    }
}
