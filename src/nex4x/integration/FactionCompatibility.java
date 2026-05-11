package nex4x.integration;

import com.fs.starfarer.api.Global;
import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;
import org.apache.log4j.Logger;

/**
 * Auto-derives nex4x faction config from NexFactionConfig when no explicit config exists.
 * Three-layer fallback: explicit -> auto-derive -> vanilla defaults.
 * See AI spec §16.
 */
public class FactionCompatibility {

    private static final Logger log = Global.getLogger(FactionCompatibility.class);

    /**
     * Ensure faction has a tendency profile. If not, derive from NexFactionConfig.
     */
    public static void ensureProfile(String factionId) {
        TendencyProfile existing = TendencyProfileLoader.getProfile(factionId);
        if (existing != null) return; // Layer 1: explicit config exists

        // Layer 2: auto-derive from NexFactionConfig
        try {
            exerelin.utilities.NexFactionConfig nfc =
                    exerelin.utilities.NexConfig.getFactionConfig(factionId);
            if (nfc != null) {
                TendencyProfile derived = deriveFromNexConfig(nfc);
                TendencyProfileLoader.registerProfile(factionId, derived);
                log.info("[Nex4x] Auto-derived profile for " + factionId + " from NexFactionConfig");
                return;
            }
        } catch (Exception e) {
            // Nex config not available
        }

        // Layer 3: vanilla defaults
        TendencyProfile defaults = new TendencyProfile();
        for (TendencyId t : TendencyId.values()) {
            defaults.set(t, 2f); // all neutral
        }
        TendencyProfileLoader.registerProfile(factionId, defaults);
        log.info("[Nex4x] Using vanilla defaults for " + factionId);
    }

    private static TendencyProfile deriveFromNexConfig(
            exerelin.utilities.NexFactionConfig nfc) {
        TendencyProfile profile = new TendencyProfile();

        // NexFactionConfig (Nex 0.12.x) does not expose scalar militarism/diplomacyPositive
        // fields. Aggregate the Map<String,Float> diplomacyPositiveChance/Negative entries
        // via reflection — these are the canonical knobs Nex actually exposes.
        float militarism = 0.5f;
        float dipPositive = 0.5f;
        try {
            Object posMap = nfc.getClass().getField("diplomacyPositiveChance").get(nfc);
            Object negMap = nfc.getClass().getField("diplomacyNegativeChance").get(nfc);
            if (posMap instanceof java.util.Map && negMap instanceof java.util.Map) {
                float posSum = sumFloats((java.util.Map<?, ?>) posMap);
                float negSum = sumFloats((java.util.Map<?, ?>) negMap);
                float total = posSum + negSum;
                if (total > 0f) {
                    dipPositive = posSum / total;
                    militarism = negSum / total;
                }
            }
        } catch (NoSuchFieldException nsf) {
            log.info("[Nex4x] NexFactionConfig diplomacy*Chance fields not present; using defaults");
        } catch (Throwable t) {
            log.warn("[Nex4x] deriveFromNexConfig: " + t.getMessage(), t);
        }

        profile.set(TendencyId.MILITARISTS, militarism * 5f);
        profile.set(TendencyId.FEDERALISTS, dipPositive * 5f);
        profile.set(TendencyId.ZEALOTS, 2f);
        profile.set(TendencyId.CORPORATISTS, 2f);
        profile.set(TendencyId.INDUSTRIALISTS, 2f);
        profile.set(TendencyId.ECOLOGISTS, 1f);

        return profile;
    }

    private static float sumFloats(java.util.Map<?, ?> m) {
        float total = 0f;
        for (Object v : m.values()) {
            if (v instanceof Number) total += ((Number) v).floatValue();
        }
        return total;
    }
}
