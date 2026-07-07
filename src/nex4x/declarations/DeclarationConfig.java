package nex4x.declarations;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Locale;

public class DeclarationConfig {
    private static final Logger log = Global.getLogger(DeclarationConfig.class);

    public int durationDays = 180;
    public float flatRepBonus = 0f;
    public float flatRepPenalty = 0f;
    public float dailyRepGain = 0f;
    public int influenceDiscountPct = 0;
    public int cbUnlockDays = 0;
    public float rippleRatio = 0f;
    public int decayDaysAfterExpiry = 0;
    public boolean bilateral = false;
    public boolean requiresReceiverConsent = false;

    private static final EnumMap<DeclarationType, DeclarationConfig> CONFIGS =
            new EnumMap<DeclarationType, DeclarationConfig>(DeclarationType.class);

    public static void load() {
        try {
            JSONObject root = Global.getSettings().loadJSON("data/config/nex4x/declarations.json");
            for (DeclarationType t : DeclarationType.values()) {
                String key = t.name().toLowerCase(Locale.ENGLISH);
                if (!root.has(key)) continue;
                JSONObject o = root.getJSONObject(key);
                DeclarationConfig c = new DeclarationConfig();
                c.durationDays          = o.optInt("durationDays", c.durationDays);
                c.flatRepBonus          = (float) o.optDouble("flatRepBonus", 0);
                c.flatRepPenalty        = (float) o.optDouble("flatRepPenalty", 0);
                c.dailyRepGain          = (float) o.optDouble("dailyRepGain", 0);
                c.influenceDiscountPct  = o.optInt("influenceDiscountPct", 0);
                c.cbUnlockDays          = o.optInt("cbUnlockDays", 0);
                c.rippleRatio           = (float) o.optDouble("rippleRatio", 0);
                c.decayDaysAfterExpiry  = o.optInt("decayDaysAfterExpiry", 0);
                c.bilateral             = o.optBoolean("bilateral", false);
                c.requiresReceiverConsent = o.optBoolean("requiresReceiverConsent", false);
                CONFIGS.put(t, c);
            }
            log.info("[Nex4x] DeclarationConfig loaded for " + CONFIGS.size() + " types.");
        } catch (Exception e) {
            log.error("[Nex4x] Failed to load declarations.json: " + e.getMessage());
        }
    }

    public static DeclarationConfig get(DeclarationType type) {
        DeclarationConfig c = CONFIGS.get(type);
        return c == null ? new DeclarationConfig() : c;
    }
}
