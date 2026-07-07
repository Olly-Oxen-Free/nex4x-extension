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
        return nex4x.util.Nex4xClock.currentAbsoluteDay();
    }

    /** Returns null if demander cannot afford costs or if input is invalid. */
    public Demand issue(String demanderId, String targetId, Demand.DemandType type,
                        String payload, float pressureCost, float influenceCost) {
        if (demanderId == null || targetId == null || type == null) {
            log.warn("[Nex4x] Demand.issue: null arg rejected");
            return null;
        }
        if (demanderId.equals(targetId)) {
            log.warn("[Nex4x] Demand.issue: self-target rejected (" + demanderId + ")");
            return null;
        }
        if (type == Demand.DemandType.CEDE_MARKET && (payload == null || payload.isEmpty())) {
            log.warn("[Nex4x] Demand.issue: CEDE_MARKET requires non-empty payload (marketId)");
            return null;
        }
        if (pressureCost < 0f || influenceCost < 0f) {
            log.warn("[Nex4x] Demand.issue: negative cost rejected");
            return null;
        }
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
        applyDemandEffect(d);
        log.info("[Nex4x] Demand accepted: " + d.getDemanderId() + " -> " + d.getTargetId()
                + " : " + d.getType());
    }

    /** Apply the in-world effect of an accepted demand. Conservative: log+status if effect not implemented. */
    private void applyDemandEffect(Demand d) {
        try {
            switch (d.getType()) {
                case TRIBUTE_CREDITS: {
                    long amount = parseLongOr(d.getPayload(), 0L);
                    if (amount > 0) {
                        com.fs.starfarer.api.campaign.FactionAPI demander =
                                Global.getSector().getFaction(d.getDemanderId());
                        if (demander != null && demander.isPlayerFaction()) {
                            Global.getSector().getPlayerFleet().getCargo().getCredits().add(amount);
                        }
                    }
                    int marketsAffected = nex4x.integration.NexDiplomacyBridge.applyTributeToFaction(
                            d.getTargetId(), d.getDemanderId());
                    log.info("[Nex4x] TRIBUTE_CREDITS persistent — applied to "
                            + marketsAffected + " market(s) of " + d.getTargetId());
                    break;
                }
                case END_WAR: {
                    com.fs.starfarer.api.campaign.FactionAPI a =
                            Global.getSector().getFaction(d.getDemanderId());
                    com.fs.starfarer.api.campaign.FactionAPI b =
                            Global.getSector().getFaction(d.getTargetId());
                    nex4x.integration.NexDiplomacyBridge.firePeaceTreaty(a, b);
                    break;
                }
                case BREAK_ALLIANCE: {
                    // Nex owns alliances — call into its API.
                    exerelin.campaign.AllianceManager am = null;
                    try {
                        am = exerelin.campaign.AllianceManager.getManager();
                    } catch (Throwable nexMissing) {
                        log.warn("[Nex4x] BREAK_ALLIANCE: Nex AllianceManager unavailable: "
                                + nexMissing.getMessage());
                    }
                    if (am != null) {
                        exerelin.campaign.alliances.Alliance alliance =
                                exerelin.campaign.AllianceManager.getFactionAlliance(d.getTargetId());
                        if (alliance != null) {
                            am.leaveAlliance(d.getTargetId(), alliance);
                            log.info("[Nex4x] BREAK_ALLIANCE: " + d.getTargetId()
                                    + " left alliance " + alliance.getName());
                        }
                    }
                    break;
                }
                case CEDE_MARKET: {
                    // Nex owns market transfers; payload = marketId.
                    com.fs.starfarer.api.campaign.econ.MarketAPI mkt =
                            Global.getSector().getEconomy().getMarket(d.getPayload());
                    com.fs.starfarer.api.campaign.FactionAPI giver =
                            Global.getSector().getFaction(d.getTargetId());
                    com.fs.starfarer.api.campaign.FactionAPI receiver =
                            Global.getSector().getFaction(d.getDemanderId());
                    if (mkt != null && giver != null && receiver != null
                            && giver.getId().equals(mkt.getFactionId())) {
                        try {
                            exerelin.campaign.SectorManager.transferMarket(
                                    mkt, receiver, giver, false, true,
                                    new java.util.ArrayList<String>(), 0f);
                            log.info("[Nex4x] CEDE_MARKET: " + mkt.getId() + " "
                                    + giver.getId() + " -> " + receiver.getId());
                        } catch (Throwable t) {
                            log.warn("[Nex4x] CEDE_MARKET transfer failed: " + t.getMessage(), t);
                        }
                    } else {
                        log.warn("[Nex4x] CEDE_MARKET: invalid marketId/payload '"
                                + d.getPayload() + "' or ownership mismatch");
                    }
                    break;
                }
                case FORCE_NEUTRALITY: {
                    // payload = third-party faction id that target must stop fighting
                    String thirdPartyId = d.getPayload();
                    if (thirdPartyId == null || thirdPartyId.isEmpty()) {
                        log.warn("[Nex4x] FORCE_NEUTRALITY: missing third-party faction id in payload");
                        break;
                    }
                    com.fs.starfarer.api.campaign.FactionAPI target =
                            Global.getSector().getFaction(d.getTargetId());
                    com.fs.starfarer.api.campaign.FactionAPI thirdParty =
                            Global.getSector().getFaction(thirdPartyId);
                    if (thirdParty == null) {
                        log.warn("[Nex4x] FORCE_NEUTRALITY: third-party faction not found: " + thirdPartyId);
                        break;
                    }
                    if (target != null && target.isHostileTo(thirdParty)) {
                        try {
                            nex4x.integration.NexDiplomacyBridge.firePeaceTreaty(target, thirdParty);
                            log.info("[Nex4x] FORCE_NEUTRALITY: " + d.getTargetId()
                                    + " forced to make peace with " + thirdPartyId);
                        } catch (Throwable t2) {
                            log.warn("[Nex4x] FORCE_NEUTRALITY peace call failed: " + t2.getMessage());
                        }
                    } else {
                        log.info("[Nex4x] FORCE_NEUTRALITY: " + d.getTargetId()
                                + " already at peace with " + thirdPartyId + " — no action needed");
                    }
                    break;
                }
                default:
                    log.warn("[Nex4x] WARN: unhandled demand type " + d.getType()
                            + " — no effect applied");
                    break;
            }
        } catch (Throwable t) {
            log.warn("[Nex4x] applyDemandEffect failed: " + t.getMessage(), t);
        }
    }

    private static long parseLongOr(String s, long fallback) {
        if (s == null) return fallback;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException nfe) { return fallback; }
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
                if (d.getStatus() == Demand.DemandStatus.ACCEPTED
                        && d.getType() == Demand.DemandType.TRIBUTE_CREDITS) {
                    int removed = nex4x.integration.NexDiplomacyBridge.removeTributeForFaction(d.getTargetId());
                    if (removed > 0) {
                        log.info("[Nex4x] TRIBUTE_CREDITS expired — removed condition from "
                                + removed + " market(s) of " + d.getTargetId());
                    }
                }
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
