package nex4x.leaders;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class DialogueSystem {
    private static final Logger log = Global.getLogger(DialogueSystem.class);

    private static DialogueSystem instance;

    private final DialogueSelector selector;
    private DialogueProvider provider;

    private DialogueSystem(DialogueSelector sel) {
        this.selector = sel;
        this.provider = new StaticPoolDialogueProvider(sel);
    }

    public static DialogueSystem get() {
        return instance;
    }

    public static void load() {
        DialoguePool baseline = DialoguePool.loadFromPath("data/config/nex4x/dialogue/baseline.json");
        DialogueSelector selector = new DialogueSelector(baseline);

        for (Personality p : Personality.values()) {
            String path = "data/config/nex4x/dialogue/personalities/" + p.name().toLowerCase() + ".json";
            if (Global.getSettings().fileExistsInCommon(path)) {
                selector.registerPersonality(p, DialoguePool.loadFromPath(path));
            }
        }
        String[] tier1Factions = {
            "hegemony", "tritachyon", "sindrian_diktat", "luddic_church",
            "luddic_path", "persean", "independent", "pirates", "remnants"
        };
        for (String fid : tier1Factions) {
            String path = "data/config/nex4x/dialogue/factions/" + fid + ".json";
            if (Global.getSettings().fileExistsInCommon(path)) {
                selector.registerFaction(fid, DialoguePool.loadFromPath(path));
            }
        }
        String[] traits = {"SEASONED", "YOUNG", "DESPERATE", "ISOLATIONIST", "EXPANSIONIST"};
        for (String t : traits) {
            String path = "data/config/nex4x/dialogue/traits/" + t.toLowerCase() + ".json";
            if (Global.getSettings().fileExistsInCommon(path)) {
                selector.registerTrait(t, DialoguePool.loadFromPath(path));
            }
        }

        instance = new DialogueSystem(selector);
        log.info("[Nex4x] DialogueSystem loaded.");
    }

    public DialogueSelector getSelector() { return selector; }
    public DialogueProvider getProvider() { return provider; }
    public void setProvider(DialogueProvider p) { this.provider = p; }

    public String resolve(LeaderProfile leader, Situation situation,
                          ReputationTier effTier, Map<String, String> context) {
        return provider.resolve(leader, situation, effTier,
                context == null ? new HashMap<String, String>() : context);
    }
}
