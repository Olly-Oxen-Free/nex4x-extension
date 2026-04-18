package nex4x.demands;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import nex4x.influence.InfluenceManager;
import nex4x.influence.InfluenceSource;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Issues demands, enforces rejection consequences via pressure/grievance injection. */
public class DemandManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Global.getLogger(DemandManager.class);

    public static final float DEFAULT_WINDOW_DAYS = 14f;
    public static final float REJECTION_GRIEVANCE = 20f;

    private final List<Demand> active = new ArrayList<Demand>();

    private static float now() {
        return Global.getSector().getClock().getDay()
                + Global.getSector().getClock().getCycle() * 365f;
    }

    /** Returns null if demander cannot afford costs. */
    public Demand issue(String demanderId, String targetId, Demand.DemandType type,
                        String payload, float pressureCost, float influenceCost) {
        InfluenceManager infl = InfluenceManager.getOrCreate();
        PressureManager press = PressureManager.getOrCreate();
        if (!infl.getLedger(demanderId).canAfford(influenceCost)) {
            log.info("[Nex4x] Demand failed: " + demanderId + " cannot afford " + influenceCost + " influence");
            return null;
        }
        if (press.getPressure(demanderId, targetId) < pressureCost) {
            log.info("[Nex4x] Demand failed: " + demanderId + "->" + targetId + " insufficient pressure");
            return null;
        }
        infl.getLedger(demanderId).spend(influenceCost, InfluenceSource.DEMAND);
        press.spend(demanderId, targetId, pressureCost);

        float t = now();
        Demand d = new Demand(demanderId, targetId, type, payload, t, t + DEFAULT_WINDOW_DAYS,
                pressureCost, influenceCost);
        active.add(d);
        log.info("[Nex4x] Demand issued: " + demanderId + " -> " + targetId + " : " + type);
        return d;
    }

    public void accept(Demand d) {
        if (d.getStatus() != Demand.DemandStatus.PENDING) return;
        d.setStatus(Demand.DemandStatus.ACCEPTED);
        log.info("[Nex4x] Demand accepted: " + d.getDemanderId() + " -> " + d.getTargetId());
    }

    public void reject(Demand d) {
        if (d.getStatus() != Demand.DemandStatus.PENDING) return;
        d.setStatus(Demand.DemandStatus.REJECTED);
        PressureManager.getOrCreate().applyEvent(
                d.getDemanderId(), d.getTargetId(),
                PressureSource.GRIEVANCE, REJECTION_GRIEVANCE);
        log.info("[Nex4x] Demand rejected: " + d.getDemanderId() + " -> " + d.getTargetId()
                + " (+" + REJECTION_GRIEVANCE + " grievance pressure)");
    }

    public List<Demand> getPending(String factionId) {
        List<Demand> result = new ArrayList<Demand>();
        for (Demand d : active) {
            if (d.getStatus() == Demand.DemandStatus.PENDING
                    && (d.getDemanderId().equals(factionId) || d.getTargetId().equals(factionId))) {
                result.add(d);
            }
        }
        return result;
    }

    public void advanceDay() {
        float t = now();
        Iterator<Demand> it = active.iterator();
        while (it.hasNext()) {
            Demand d = it.next();
            if (d.getStatus() == Demand.DemandStatus.PENDING && t >= d.getExpiryDay()) {
                d.setStatus(Demand.DemandStatus.EXPIRED);
                PressureManager.getOrCreate().applyEvent(
                        d.getDemanderId(), d.getTargetId(),
                        PressureSource.GRIEVANCE, REJECTION_GRIEVANCE * 0.5f);
                log.info("[Nex4x] Demand expired (silent refusal): " + d.getDemanderId()
                        + " -> " + d.getTargetId());
            }
            if (d.getStatus() != Demand.DemandStatus.PENDING && t > d.getExpiryDay() + 60f) {
                it.remove();
            }
        }
    }

    public List<Demand> getAll() { return active; }

    public static DemandManager get() {
        Object raw = Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_DEMAND_MANAGER);
        if (raw instanceof DemandManager) return (DemandManager) raw;
        return null;
    }

    public static DemandManager getOrCreate() {
        DemandManager mgr = get();
        if (mgr == null) {
            mgr = new DemandManager();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_DEMAND_MANAGER, mgr);
        }
        return mgr;
    }
}
