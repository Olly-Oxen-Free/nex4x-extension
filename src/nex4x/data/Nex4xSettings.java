package nex4x.data;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;
import org.json.JSONObject;

public class Nex4xSettings {

    private static final Logger log = Global.getLogger(Nex4xSettings.class);

    public static int maxMemoriesPerPair = 50;
    public static int badgeWarmongerThreshold = 3;
    public static int badgePeacemakerThreshold = 3;
    public static float beliefStr1ImpactMult = 1.25f;
    public static float beliefStr2ImpactMult = 1.75f;
    public static float beliefStr3ImpactMult = 2.5f;
    public static float beliefStr1DecayMod = 0.8f;
    public static float beliefStr2DecayMod = 0.5f;
    public static float beliefStr3DecayMod = 0.1f;

    // AIProposalManager cooldowns (days)
    public static float aiProposalCooldownDays = 60f;
    public static float aiProposalUrgentCooldownDays = 30f;

    // NegotiationPopUpDialog assessment visibility: "always_visible" or "requires_intel"
    public static String negotiationAssessmentMode = "always_visible";

    public static void load() throws Exception {
        JSONObject json = Global.getSettings().getMergedJSONForMod(
                Nex4xConstants.PATH_SETTINGS, Nex4xConstants.MOD_ID);

        maxMemoriesPerPair = json.optInt("maxMemoriesPerPair", 50);
        badgeWarmongerThreshold = json.optInt("badgeWarmongerThreshold", 3);
        badgePeacemakerThreshold = json.optInt("badgePeacemakerThreshold", 3);
        beliefStr1ImpactMult = (float) json.optDouble("beliefStrength1ImpactMult", 1.25);
        beliefStr2ImpactMult = (float) json.optDouble("beliefStrength2ImpactMult", 1.75);
        beliefStr3ImpactMult = (float) json.optDouble("beliefStrength3ImpactMult", 2.5);
        beliefStr1DecayMod = (float) json.optDouble("beliefStrength1DecayMod", 0.8);
        beliefStr2DecayMod = (float) json.optDouble("beliefStrength2DecayMod", 0.5);
        beliefStr3DecayMod = (float) json.optDouble("beliefStrength3DecayMod", 0.1);
        aiProposalCooldownDays = (float) json.optDouble("aiProposalCooldownDays", 60.0);
        aiProposalUrgentCooldownDays = (float) json.optDouble("aiProposalUrgentCooldownDays", 30.0);
        negotiationAssessmentMode = json.optString("negotiationAssessmentMode", "always_visible");

        log.info("[Nex4x] Settings loaded (maxMemories=" + maxMemoriesPerPair + ")");
    }
}
