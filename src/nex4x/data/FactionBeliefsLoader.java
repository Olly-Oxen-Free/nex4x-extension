package nex4x.data;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FactionBeliefsLoader {

    private static final Logger log = Global.getLogger(FactionBeliefsLoader.class);
    private static final Map<String, FactionBeliefs> factionBeliefs = new HashMap<String, FactionBeliefs>();

    /**
     * Belief ids that default to SECRET when a faction JSON entry omits {@code visibility},
     * so low-intel viewers do not see them as public by mistake.
     */
    private static final Set<String> DEFAULT_SECRET_IF_VISIBILITY_OMITTED = new HashSet<String>(Arrays.asList(
            "seeks_sector_hegemony",
            "seeks_economic_dominance",
            "claims_domain_territory",
            "claims_league_territory",
            "absorb_fringe_colonies",
            "undermine_heg_regulatory",
            "targets_poorly_defended",
            "annihilate_all_ai_tech",
            "violence_is_purification",
            "unknown_purpose",
            "loyalty_above_morality",
            "fuel_monopoly_nonnegotiable",
            "expand_fuel_territory",
            "diktat_authority_absolute",
            "military_economic_enforcement",
            "profit_any_means",
            "respects_strength",
            "elite_military",
            "hostile_organic"
    ));

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
                String visibility;
                if (!entry.has("visibility") || entry.optString("visibility", "").trim().isEmpty()) {
                    visibility = DEFAULT_SECRET_IF_VISIBILITY_OMITTED.contains(beliefId)
                            ? "secret" : "public";
                } else {
                    visibility = entry.getString("visibility");
                }

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
