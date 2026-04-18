package nex4x.vassals;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages all overlord-vassal relationships. Daily advance tracks
 * independence desire and triggers rebellion flags.
 */
public class VassalManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Global.getLogger(VassalManager.class);

    private final List<VassalRelation> relations = new ArrayList<VassalRelation>();

    public VassalRelation vassalize(String overlordId, String vassalId, VassalTier tier) {
        freeVassal(vassalId);
        float day = Global.getSector().getClock().getDay()
                + Global.getSector().getClock().getCycle() * 365f;
        VassalRelation rel = new VassalRelation(overlordId, vassalId, tier, day);
        relations.add(rel);
        log.info("[Nex4x] Vassalized: " + overlordId + " -> " + vassalId + " (" + tier.displayName + ")");
        return rel;
    }

    public void freeVassal(String vassalId) {
        Iterator<VassalRelation> it = relations.iterator();
        while (it.hasNext()) {
            VassalRelation r = it.next();
            if (r.getVassalId().equals(vassalId)) {
                it.remove();
                log.info("[Nex4x] Vassal freed: " + vassalId);
            }
        }
    }

    public VassalRelation getVassalRelation(String vassalId) {
        for (VassalRelation r : relations) if (r.getVassalId().equals(vassalId)) return r;
        return null;
    }

    public List<VassalRelation> getVassals(String overlordId) {
        List<VassalRelation> result = new ArrayList<VassalRelation>();
        for (VassalRelation r : relations) if (r.getOverlordId().equals(overlordId)) result.add(r);
        return result;
    }

    public boolean isVassal(String factionId) { return getVassalRelation(factionId) != null; }

    public boolean isOverlordOf(String overlordId, String vassalId) {
        VassalRelation r = getVassalRelation(vassalId);
        return r != null && r.getOverlordId().equals(overlordId);
    }

    public float getTotalTributeIncome(String overlordId) {
        float total = 0f;
        for (VassalRelation r : getVassals(overlordId)) {
            total += r.getTier().incomeShare * estimateFactionIncome(r.getVassalId());
        }
        return total;
    }

    private float estimateFactionIncome(String factionId) {
        float total = 0f;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.getFactionId().equals(factionId) && !m.isHidden()) total += m.getSize() * 10000f;
        }
        return total;
    }

    public void advanceDay() {
        for (VassalRelation r : relations) {
            if (r.isRebellionActive()) continue;
            float overlordWeakness = estimateWeakness(r.getOverlordId());
            if (r.advanceDay(overlordWeakness, 0f)) {
                r.setRebellionActive(true);
                log.info("[Nex4x] Independence rebellion: " + r.getVassalId()
                        + " against " + r.getOverlordId());
            }
        }
    }

    private float estimateWeakness(String factionId) { return 0.3f; }

    public List<VassalRelation> getAllRelations() { return relations; }

    public static VassalManager get() {
        Object raw = Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_VASSAL_MANAGER);
        if (raw instanceof VassalManager) return (VassalManager) raw;
        return null;
    }

    public static VassalManager getOrCreate() {
        VassalManager mgr = get();
        if (mgr == null) {
            mgr = new VassalManager();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_VASSAL_MANAGER, mgr);
        }
        return mgr;
    }
}
