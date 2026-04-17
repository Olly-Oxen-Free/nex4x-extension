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

        // Map NexFactionConfig fields to tendencies
        // Use reflection to avoid compile errors on varying NexFactionConfig versions
        float militarism = 0.5f;
        float dipPositive = 0.5f;
        try {
            militarism = nfc.getClass().getField("militarism").getFloat(nfc);
            dipPositive = nfc.getClass().getField("diplomacyPositive").getFloat(nfc);
        } catch (Exception e) {
            // field not found in this version of Nex — use defaults
        }

        profile.set(TendencyId.MILITARISTS, militarism * 5f);
        profile.set(TendencyId.FEDERALISTS, dipPositive * 5f);
        profile.set(TendencyId.ZEALOTS, 2f);
        profile.set(TendencyId.CORPORATISTS, 2f);
        profile.set(TendencyId.INDUSTRIALISTS, 2f);
        profile.set(TendencyId.ECOLOGISTS, 1f);

        return profile;
    }
}
