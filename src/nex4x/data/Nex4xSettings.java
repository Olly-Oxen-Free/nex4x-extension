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
    /**
     * Optional campaign-start dampener: no AI deal intel until this many in-sector days
     * after first manager tick. Primary gating is relation + NAP logic in {@code AutoNegotiator}.
     */
    public static float aiProposalMinGameDays = 0f;
    /** If true, AI factions only propose to the player once the player faction owns at least one market. */
    public static boolean aiProposalRequirePlayerMarket = true;

    // AI-initiated NAP (AutoNegotiator): friendly path vs wary path + defensive motive
    /** Minimum relation (same scale as {@code FactionAPI#getRelationship}) for “warm” NAP offers. */
    public static float aiNapFriendlyRelMin = 22f;
    /** Inclusive lower bound for wary-band NAP (slightly negative / cool relations). */
    public static float aiNapWaryRelMin = -25f;
    /** Exclusive upper bound for wary-band NAP (must stay below {@link #aiNapFriendlyRelMin}). */
    public static float aiNapWaryRelMax = 12f;
    /** Wary NAP: AI desperation score floor (see {@code DesperationCalculator}). */
    public static float aiNapDefensiveDesperationMin = 25f;
    /** Wary NAP: player→AI pressure floor (see {@code PressureManager#getPressure}). */
    public static float aiNapDefensivePressureMin = 20f;

    // NegotiationPopUpDialog assessment visibility: "always_visible" or "requires_intel"
    public static String negotiationAssessmentMode = "always_visible";

    // When true, leaves Nex StrategicAI + DiplomacyProfileIntel visible.
    // When false (default), FactionBrowserIntel fully replaces them.
    public static boolean showLegacyNexIntels = false;

    /**
     * Unused: {@link nex4x.Nex4xModPlugin} always adds Faction Browser intel.
     * Kept for merged JSON compatibility.
     */
    public static boolean showLegacyFactionBrowserIntel = false;

    /**
     * Ashlib {@code CommandTabTracker} only injects tabs on the <b>colony / Outposts</b> industry screen.
     * It uses this value as a key into its internal map, then resolves a real button via
     * {@code tryToGetButtonProd}. Valid keys match that map (vanilla): {@code income}, {@code colonies},
     * {@code orders}, {@code doctrine & blueprints}, {@code custom production}. Default {@code income}
     * matches the same probe Ashlib uses before running listeners. Values like {@code cargo} or {@code fleet}
     * are wrong here and will null-crash in {@code insertButton}.
     */
    public static String commandTabPlaceNearButton = "income";

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
        aiProposalMinGameDays = (float) json.optDouble("aiProposalMinGameDays", 0.0);
        aiProposalRequirePlayerMarket = json.optBoolean("aiProposalRequirePlayerMarket", true);
        aiNapFriendlyRelMin = (float) json.optDouble("aiNapFriendlyRelMin", 22.0);
        aiNapWaryRelMin = (float) json.optDouble("aiNapWaryRelMin", -25.0);
        aiNapWaryRelMax = (float) json.optDouble("aiNapWaryRelMax", 12.0);
        aiNapDefensiveDesperationMin = (float) json.optDouble("aiNapDefensiveDesperationMin", 25.0);
        aiNapDefensivePressureMin = (float) json.optDouble("aiNapDefensivePressureMin", 20.0);
        negotiationAssessmentMode = json.optString("negotiationAssessmentMode", "always_visible");
        showLegacyNexIntels = json.optBoolean("showLegacyNexIntels", false);
        showLegacyFactionBrowserIntel = json.optBoolean("showLegacyFactionBrowserIntel", false);
        commandTabPlaceNearButton = json.optString("commandTabPlaceNearButton", "income").trim();
        if (commandTabPlaceNearButton.isEmpty()) {
            commandTabPlaceNearButton = "income";
        }
        commandTabPlaceNearButton = commandTabPlaceNearButton.toLowerCase();

        log.info("[Nex4x] Settings loaded (maxMemories=" + maxMemoriesPerPair + ")");
    }
}
