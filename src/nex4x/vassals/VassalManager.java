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
        int applied = nex4x.integration.NexDiplomacyBridge.applyTributeToFaction(vassalId, overlordId);
        log.info("[Nex4x] Vassal " + vassalId + " tribute condition on "
                + applied + " market(s) -> " + overlordId);
        return rel;
    }

    public void freeVassal(String vassalId) {
        int removed = nex4x.integration.NexDiplomacyBridge.removeTributeForFaction(vassalId);
        log.info("[Nex4x] Vassal " + vassalId + " freed — removed tribute from "
                + removed + " market(s)");
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
        if (factionId == null) return 0f;
        float total = 0f;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!factionId.equals(m.getFactionId())) continue;
            if (m.isHidden()) continue;
            total += Math.max(0f, m.getNetIncome());
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
        for (VassalRelation r : relations) {
            if (r.isRebellionActive()) continue;
            nex4x.integration.NexDiplomacyBridge.applyTributeToFaction(r.getVassalId(), r.getOverlordId());
        }
    }

    /**
     * Compute overlord weakness in [0, 1]. Lower rank-percentile (top of FactionPowerRankings)
     * means stronger overlord and lower weakness; bottom-rank means high weakness (~1.0).
     */
    private float estimateWeakness(String factionId) {
        try {
            nex4x.util.FactionPowerRankings.rebuild();
            String label = nex4x.util.FactionPowerRankings.getRankLabel(factionId);
            // Label format: "Rank {r}/{n}". Parse defensively.
            if (label != null && label.startsWith("Rank ")) {
                String body = label.substring(5);
                int slash = body.indexOf('/');
                if (slash > 0) {
                    int r = Integer.parseInt(body.substring(0, slash));
                    int n = Integer.parseInt(body.substring(slash + 1));
                    if (n > 0) {
                        // r=1 (strongest) -> ~0.0, r=n (weakest) -> ~1.0
                        return Math.max(0f, Math.min(1f, (float) (r - 1) / (float) n));
                    }
                }
            }
        } catch (Throwable t) {
            log.warn("[Nex4x] estimateWeakness fallback: " + t.getMessage());
        }
        return 0.3f;
    }

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
