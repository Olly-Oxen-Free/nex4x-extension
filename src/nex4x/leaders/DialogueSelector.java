package nex4x.leaders;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class DialogueSelector {
    private static final Logger log = Global.getLogger(DialogueSelector.class);

    private final DialoguePool baseline;
    private final Map<Personality, DialoguePool> personalityPools = new HashMap<Personality, DialoguePool>();
    private final Map<String, DialoguePool> factionPools = new HashMap<String, DialoguePool>();
    private final Map<String, DialoguePool> traitPools = new HashMap<String, DialoguePool>();
    private final Set<String> loggedMissing = new HashSet<String>();
    private final Random rng = new Random();

    public DialogueSelector(DialoguePool baseline) {
        this.baseline = baseline;
    }

    public void registerPersonality(Personality p, DialoguePool pool) {
        personalityPools.put(p, pool);
    }
    public void registerFaction(String factionId, DialoguePool pool) {
        factionPools.put(factionId, pool);
    }
    public void registerTrait(String traitId, DialoguePool pool) {
        traitPools.put(traitId, pool);
    }

    public String select(LeaderProfile leader, Situation situation,
                         ReputationTier effTier, Map<String, String> context) {
        List<String> pool = new ArrayList<String>();
        pool.addAll(baseline.get(situation, effTier));
        DialoguePool pp = personalityPools.get(leader.getPersonality());
        if (pp != null) pool.addAll(pp.get(situation, effTier));
        DialoguePool fp = factionPools.get(leader.getFactionId());
        if (fp != null) pool.addAll(fp.get(situation, effTier));
        for (String trait : leader.getTraits()) {
            DialoguePool tp = traitPools.get(trait);
            if (tp != null) pool.addAll(tp.get(situation, effTier));
        }
        if (pool.isEmpty()) {
            pool.addAll(baseline.get(situation, ReputationTier.NEUTRAL));
            if (pp != null) pool.addAll(pp.get(situation, ReputationTier.NEUTRAL));
            if (fp != null) pool.addAll(fp.get(situation, ReputationTier.NEUTRAL));
        }
        if (pool.isEmpty()) {
            String key = situation.name() + ":" + effTier.name();
            if (loggedMissing.add(key)) {
                log.warn("[Nex4x] Dialogue pool empty for " + key + " — using hardcoded fallback.");
            }
            return substituteTokens("...", context);
        }
        String picked = pool.get(rng.nextInt(pool.size()));
        return substituteTokens(picked, context);
    }

    static String substituteTokens(String line, Map<String, String> context) {
        if (context == null || context.isEmpty()) return line;
        String out = line;
        for (Map.Entry<String, String> e : context.entrySet()) {
            String token = "{" + e.getKey() + "}";
            out = out.replace(token, e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    public DialoguePool getBaseline() { return baseline; }
}
