package nex4x.policies;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;
import nex4x.politics.DynamicModifierManager;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Manages faction policies. Policies are tendency-gated and cost influence upkeep.
 * Active policies reinforce the gating tendency (+0.1/cycle drift).
 */
public class PolicyManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Global.getLogger(PolicyManager.class);

    public static final float TENDENCY_DRIFT_PER_CYCLE = 0.1f;

    private final Map<String, List<Policy>> factionPolicies = new HashMap<String, List<Policy>>();

    /** Adopt a policy. Returns false if tendency requirement not met. */
    public boolean adoptPolicy(String factionId, PolicyType type) {
        TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
        if (profile == null) return false;

        float dynamicShift = 0f;
        DynamicModifierManager dm = DynamicModifierManager.get();
        if (dm != null) dynamicShift = dm.getTotalShift(factionId, type.requiredTendency);
        float effective = profile.getWeight(type.requiredTendency) + dynamicShift;
        if (effective < type.requiredWeight) {
            log.info("[Nex4x] Policy rejected: " + factionId + " " + type.displayName
                    + " requires " + type.requiredTendency + ">=" + type.requiredWeight
                    + " (have " + effective + ")");
            return false;
        }
        if (hasPolicy(factionId, type)) return false;

        float day = Global.getSector().getClock().getDay()
                + Global.getSector().getClock().getCycle() * 365f;
        getPolicies(factionId).add(new Policy(type, day));
        log.info("[Nex4x] Policy adopted: " + factionId + " " + type.displayName);
        return true;
    }

    public void revokePolicy(String factionId, PolicyType type) {
        for (Policy p : getPolicies(factionId)) {
            if (p.getType() == type && p.isActive()) {
                p.revoke();
                log.info("[Nex4x] Policy revoked: " + factionId + " " + type.displayName);
                return;
            }
        }
    }

    public float getTotalUpkeep(String factionId) {
        float total = 0f;
        for (Policy p : getPolicies(factionId)) if (p.isActive()) total += p.getUpkeep();
        return total;
    }

    public List<Policy> getActivePolicies(String factionId) {
        List<Policy> out = new ArrayList<Policy>();
        for (Policy p : getPolicies(factionId)) if (p.isActive()) out.add(p);
        return out;
    }

    public boolean hasPolicy(String factionId, PolicyType type) {
        for (Policy p : getPolicies(factionId)) {
            if (p.getType() == type && p.isActive()) return true;
        }
        return false;
    }

    public float getPolicyModifier(String factionId, String stat) {
        float total = 0f;
        for (Policy p : getActivePolicies(factionId)) {
            total += PolicyEffect.getModifier(p.getType(), stat);
        }
        return total;
    }

    public void advanceDay() {
        for (Map.Entry<String, List<Policy>> entry : factionPolicies.entrySet()) {
            String factionId = entry.getKey();
            Iterator<Policy> it = entry.getValue().iterator();
            while (it.hasNext()) {
                Policy p = it.next();
                if (!p.isActive()) { it.remove(); continue; }

                TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
                if (profile == null) continue;
                float dynamicShift = 0f;
                DynamicModifierManager dm = DynamicModifierManager.get();
                if (dm != null) dynamicShift = dm.getTotalShift(factionId, p.getType().requiredTendency);
                float eff = profile.getWeight(p.getType().requiredTendency) + dynamicShift;
                if (eff < p.getType().requiredWeight - 0.5f) {
                    p.revoke();
                    log.info("[Nex4x] Policy auto-revoked (tendency dropped): " + factionId
                            + " " + p.getType().displayName);
                }
            }
        }
    }

    private List<Policy> getPolicies(String factionId) {
        List<Policy> list = factionPolicies.get(factionId);
        if (list == null) {
            list = new ArrayList<Policy>();
            factionPolicies.put(factionId, list);
        }
        return list;
    }

    public static PolicyManager get() {
        Object raw = Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_POLICY_MANAGER);
        if (raw instanceof PolicyManager) return (PolicyManager) raw;
        return null;
    }

    public static PolicyManager getOrCreate() {
        PolicyManager mgr = get();
        if (mgr == null) {
            mgr = new PolicyManager();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_POLICY_MANAGER, mgr);
        }
        return mgr;
    }
}
