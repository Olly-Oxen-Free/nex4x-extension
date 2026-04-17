package nex4x.data;

import com.fs.starfarer.api.Global;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitIds;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class TendencyProfileLoader {

    private static final Logger log = Global.getLogger(TendencyProfileLoader.class);
    private static final Map<String, TendencyProfile> profiles = new HashMap<String, TendencyProfile>();

    private static final Map<String, Map<TendencyId, Float>> TRAIT_TENDENCY_MAP =
            new HashMap<String, Map<TendencyId, Float>>();

    static {
        addTraitMapping(TraitIds.STALWART, TendencyId.MILITARISTS, 2f);
        addTraitMapping(TraitIds.IRREDENTIST, TendencyId.MILITARISTS, 2f);
        addTraitMapping(TraitIds.FOREVERWAR, TendencyId.MILITARISTS, 2f);
        addTraitMapping(TraitIds.TEMPERAMENTAL, TendencyId.MILITARISTS, 1f);
        addTraitMapping(TraitIds.HELPS_ALLIES, TendencyId.FEDERALISTS, 2f);
        addTraitMapping(TraitIds.LAW_AND_ORDER, TendencyId.FEDERALISTS, 1f);
        addTraitMapping(TraitIds.LAW_AND_ORDER, TendencyId.INDUSTRIALISTS, 1f);
        addTraitMapping(TraitIds.SELFRIGHTEOUS, TendencyId.ZEALOTS, 2f);
        addTraitMapping(TraitIds.MONSTROUS, TendencyId.ZEALOTS, 1f);
        addTraitMapping(TraitIds.HATES_AI, TendencyId.ZEALOTS, 2f);
        addTraitMapping(TraitIds.DISLIKES_AI, TendencyId.ZEALOTS, 1f);
        addTraitMapping(TraitIds.MONOPOLIST, TendencyId.CORPORATISTS, 2f);
        addTraitMapping(TraitIds.DEVIOUS, TendencyId.CORPORATISTS, 1f);
        addTraitMapping(TraitIds.ENVIOUS, TendencyId.CORPORATISTS, 1f);
        addTraitMapping(TraitIds.PREDATORY, TendencyId.CORPORATISTS, 1f);
        addTraitMapping(TraitIds.PREDATORY, TendencyId.MILITARISTS, 1f);
        addTraitMapping(TraitIds.ANARCHIST, TendencyId.MILITARISTS, 1f);
        addTraitMapping(TraitIds.ANARCHIST, TendencyId.ZEALOTS, 1f);
        addTraitMapping(TraitIds.PACIFIST, TendencyId.FEDERALISTS, 1f);
        addTraitMapping(TraitIds.PACIFIST, TendencyId.ECOLOGISTS, 1f);
        addTraitMapping(TraitIds.NEUTRALIST, TendencyId.ECOLOGISTS, 1f);
        addTraitMapping(TraitIds.NEUTRALIST, TendencyId.CORPORATISTS, 1f);
        addTraitMapping(TraitIds.LOWPROFILE, TendencyId.ECOLOGISTS, 1f);
        addTraitMapping(TraitIds.LOWPROFILE, TendencyId.CORPORATISTS, 1f);
        addTraitMapping(TraitIds.WEAK_WILLED, TendencyId.FEDERALISTS, 1f);
        addTraitMapping(TraitIds.LIKES_AI, TendencyId.CORPORATISTS, 1f);
        addTraitMapping(TraitIds.SUBMISSIVE, TendencyId.FEDERALISTS, 1f);
    }

    private static void addTraitMapping(String traitId, TendencyId tendency, float weight) {
        Map<TendencyId, Float> map = TRAIT_TENDENCY_MAP.get(traitId);
        if (map == null) {
            map = new EnumMap<TendencyId, Float>(TendencyId.class);
            TRAIT_TENDENCY_MAP.put(traitId, map);
        }
        Float existing = map.get(tendency);
        map.put(tendency, (existing != null ? existing : 0f) + weight);
    }

    public static void loadProfiles() throws Exception {
        profiles.clear();

        JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "faction_id", Nex4xConstants.PATH_TENDENCY_PROFILES, Nex4xConstants.MOD_ID);

        for (int i = 0; i < csv.length(); i++) {
            JSONObject row = csv.getJSONObject(i);
            String factionId = row.getString("faction_id");

            EnumMap<TendencyId, Float> weights = new EnumMap<TendencyId, Float>(TendencyId.class);
            weights.put(TendencyId.MILITARISTS, (float) row.optDouble("mil", 0));
            weights.put(TendencyId.FEDERALISTS, (float) row.optDouble("fed", 0));
            weights.put(TendencyId.ZEALOTS, (float) row.optDouble("zea", 0));
            weights.put(TendencyId.CORPORATISTS, (float) row.optDouble("cor", 0));
            weights.put(TendencyId.INDUSTRIALISTS, (float) row.optDouble("ind", 0));
            weights.put(TendencyId.ECOLOGISTS, (float) row.optDouble("eco", 0));

            profiles.put(factionId, new TendencyProfile(weights));
            log.info("[Nex4x] Loaded tendency profile for " + factionId
                    + " (total: " + profiles.get(factionId).getTotal() + ")");
        }
    }

    public static TendencyProfile getProfile(String factionId) {
        TendencyProfile explicit = profiles.get(factionId);
        if (explicit != null) return explicit;

        TendencyProfile derived = deriveFromTraits(factionId);
        profiles.put(factionId, derived);
        return derived;
    }

    public static TendencyProfile deriveFromTraits(String factionId) {
        List<String> traits = DiplomacyTraits.getFactionTraits(factionId);
        EnumMap<TendencyId, Float> raw = new EnumMap<TendencyId, Float>(TendencyId.class);
        for (TendencyId t : TendencyId.values()) raw.put(t, 0f);

        for (String traitId : traits) {
            Map<TendencyId, Float> mapping = TRAIT_TENDENCY_MAP.get(traitId);
            if (mapping == null) continue;
            for (Map.Entry<TendencyId, Float> e : mapping.entrySet()) {
                raw.put(e.getKey(), raw.get(e.getKey()) + e.getValue());
            }
        }

        float total = 0;
        for (float v : raw.values()) total += v;

        if (total <= 0) {
            for (TendencyId t : TendencyId.values()) {
                raw.put(t, 10f / TendencyId.values().length);
            }
        } else {
            float scale = 10f / total;
            for (TendencyId t : TendencyId.values()) {
                raw.put(t, Math.round(raw.get(t) * scale * 2f) / 2f);
            }
        }

        log.info("[Nex4x] Auto-derived tendency profile for " + factionId
                + " from " + traits.size() + " traits");
        return new TendencyProfile(raw);
    }

    public static boolean hasExplicitProfile(String factionId) {
        return profiles.containsKey(factionId);
    }

    /** Register a profile at runtime (e.g. auto-derived by FactionCompatibility). */
    public static void registerProfile(String factionId, TendencyProfile profile) {
        profiles.put(factionId, profile);
    }
}
