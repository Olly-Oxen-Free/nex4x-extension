package nex4x.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.casusbelli.CasusBelliType;
import nex4x.managers.Nex4xManager;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Routes campaign events to the appropriate handler layer.
 * Events are classified into three urgency tiers:
 * - DIPLOMATIC_URGENT: flag for immediate executor evaluation (war declarations, etc.)
 * - DIPLOMATIC_ROUTINE: goal manager picks up next daily update
 * - MILITARY_STATE_CHANGE: flag for StrategicAI + goal progress update
 * See AI spec §6.
 */
public class ReactiveHandler implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(ReactiveHandler.class);

    public enum EventClass {
        DIPLOMATIC_URGENT,
        DIPLOMATIC_ROUTINE,
        MILITARY_STATE_CHANGE,
        UNCLASSIFIED
    }

    /** Pending urgent events, cleared each daily tick. */
    private final List<PendingEvent> urgentQueue = new ArrayList<PendingEvent>();

    /**
     * Route a campaign event to the appropriate handler.
     * Called from Nex4xEventListener.
     */
    public void routeEvent(String eventType, String sourceFactionId,
                            String targetFactionId, Object data) {
        EventClass classification = classify(eventType);

        if (log.isDebugEnabled()) {
            log.debug("[Nex4x] ReactiveHandler: " + eventType
                    + " (" + sourceFactionId + " -> " + targetFactionId + ")"
                    + " class=" + classification);
        }

        switch (classification) {
            case DIPLOMATIC_URGENT:
                urgentQueue.add(new PendingEvent(eventType, sourceFactionId, targetFactionId, data));
                break;
            case DIPLOMATIC_ROUTINE:
                // Goal manager handles on next daily tick — no action needed
                break;
            case MILITARY_STATE_CHANGE:
                handleMilitaryStateChange(eventType, sourceFactionId, targetFactionId);
                break;
            case UNCLASSIFIED:
            default:
                break;
        }
    }

    /**
     * Process urgent queue. Called at start of each daily AI tick.
     */
    public void processUrgentQueue(Nex4xManager mgr) {
        if (urgentQueue.isEmpty()) return;

        for (PendingEvent event : urgentQueue) {
            try {
                processUrgentEvent(event, mgr);
            } catch (Exception e) {
                log.error("[Nex4x] ReactiveHandler failed on event " + event.type
                        + ": " + e.getMessage());
            }
        }
        urgentQueue.clear();
    }

    private void processUrgentEvent(PendingEvent event, Nex4xManager mgr) {
        // Force executor to re-evaluate immediately
        if (event.sourceFactionId != null) {
            StrategicGoalManager goalMgr = mgr.getGoalManager(event.sourceFactionId);
            DiplomaticExecutor executor = mgr.getExecutor(event.sourceFactionId);
            executor.advanceDay(goalMgr, mgr.getGrandStrategy());
        }
        if (event.targetFactionId != null) {
            StrategicGoalManager goalMgr = mgr.getGoalManager(event.targetFactionId);
            DiplomaticExecutor executor = mgr.getExecutor(event.targetFactionId);
            executor.advanceDay(goalMgr, mgr.getGrandStrategy());
        }
    }

    private void handleMilitaryStateChange(String eventType, String sourceFactionId,
                                            String targetFactionId) {
        // Flag goal managers for immediate recalculation
        // (actual recalc happens on next daily tick via Nex4xManager)
        if (log.isDebugEnabled()) {
            log.debug("[Nex4x] Military state change: " + eventType
                    + " " + sourceFactionId + " -> " + targetFactionId);
        }
    }

    /** Called from {@link nex4x.listeners.Nex4xEventListener#reportMarketTransfered}. */
    public void onMarketTransferred(Nex4xManager mgr, MarketAPI market, FactionAPI newOwner,
                                    FactionAPI oldOwner, boolean isCapture) {
        if (mgr == null || market == null || newOwner == null || oldOwner == null) return;
        String detail = market.getName();
        mgr.getMemoryManager().createMemory(
                isCapture ? "market_capture" : "market_transfer",
                newOwner.getId(), oldOwner.getId(), detail);
        PressureManager.getOrCreate().applyEvent(
                newOwner.getId(), oldOwner.getId(), PressureSource.GRIEVANCE, isCapture ? 50f : 20f);
        try {
            mgr.getCasusBelliManager().addCB(
                    oldOwner.getId(), newOwner.getId(), CasusBelliType.RETALIATION,
                    (isCapture ? "Capture: " : "Transfer: ") + detail);
        } catch (Exception ignore) { }
        if (isCapture) {
            mgr.getBadgeManager().recordAggression(newOwner.getId());
        }
        routeEvent("market_captured", newOwner.getId(), oldOwner.getId(), detail);
    }

    /** Raid for valuables completed (player or AI context). */
    public void onRaidValuables(Nex4xManager mgr, MarketAPI market, FactionAPI attacker) {
        if (mgr == null || market == null) return;
        String fid = market.getFactionId();
        if (attacker != null) {
            mgr.getMemoryManager().createMemory("raid_valuables", attacker.getId(), fid, market.getName());
            PressureManager.getOrCreate().applyEvent(attacker.getId(), fid, PressureSource.MILITARY, 25f);
            try {
                mgr.getCasusBelliManager().addCB(fid, attacker.getId(), CasusBelliType.RETALIATION,
                        "Raid: " + market.getName());
            } catch (Exception ignore) { }
        }
        routeEvent("raid_valuables", attacker != null ? attacker.getId() : null, fid, market.getName());
    }

    /** Economic pressure from a sustained blockade (hook when a blockade listener is available). */
    public void onBlockadeImpactedMarket(Nex4xManager mgr, com.fs.starfarer.api.campaign.econ.MarketAPI market,
                                         com.fs.starfarer.api.campaign.FactionAPI attacker) {
        if (mgr == null || market == null || attacker == null) return;
        String fid = market.getFactionId();
        mgr.getMemoryManager().createMemory("blockade_impact", attacker.getId(), fid, market.getName());
        PressureManager.getOrCreate().applyEvent(attacker.getId(), fid, PressureSource.ECONOMIC_IMPORT, 18f);
        routeEvent("blockade", attacker.getId(), fid, market.getName());
    }

    /** Player commission changed — memory for the dropped employer. */
    public void onPlayerCommissionChange(Nex4xManager mgr, String previousCommissionFactionId,
                                         String newCommissionFactionId) {
        if (mgr == null) return;
        String playerId = Global.getSector().getPlayerFaction().getId();
        if (previousCommissionFactionId != null && !previousCommissionFactionId.isEmpty()
                && !previousCommissionFactionId.equals(newCommissionFactionId)) {
            mgr.getMemoryManager().createMemory(
                    "commission_change", playerId, previousCommissionFactionId,
                    newCommissionFactionId != null ? ("new:" + newCommissionFactionId) : "none");
        }
        routeEvent("commission_change", playerId, previousCommissionFactionId, newCommissionFactionId);
    }

    /** Player finished a bounty engagement (light memory). */
    public void onPlayerBountyEngagement(Nex4xManager mgr, String enemyFactionId) {
        if (mgr == null || enemyFactionId == null) return;
        String playerId = Global.getSector().getPlayerFaction().getId();
        mgr.getMemoryManager().createMemory("bounty_completed", playerId, enemyFactionId, null);
    }

    /** Reputation shift involving the player. */
    public void onPlayerReputationChange(Nex4xManager mgr, String factionId, float delta) {
        if (mgr == null || factionId == null) return;
        String playerId = Global.getSector().getPlayerFaction().getId();
        if (delta < -0.02f) {
            mgr.getMemoryManager().createMemory("rep_loss", factionId, playerId, String.format("%.2f", delta));
        } else if (delta > 0.02f) {
            mgr.getMemoryManager().createMemory("rep_gain", playerId, factionId, String.format("%.2f", delta));
        }
        routeEvent("reputation_change", playerId, factionId, Float.valueOf(delta));
    }

    private EventClass classify(String eventType) {
        if (eventType == null) return EventClass.UNCLASSIFIED;

        switch (eventType) {
            case "declare_war":
            case "war_declared_on":
            case "ceasefire_broken":
                return EventClass.DIPLOMATIC_URGENT;

            case "make_peace":
            case "ceasefire":
            case "agreement_proposed":
            case "agreement_expired":
            case "agreement_cancelled":
            case "blockade":
            case "commission_change":
                return EventClass.DIPLOMATIC_ROUTINE;

            case "battle_result":
            case "market_captured":
            case "fleet_destroyed":
                return EventClass.MILITARY_STATE_CHANGE;

            default:
                return EventClass.UNCLASSIFIED;
        }
    }

    private static class PendingEvent implements Serializable {
        private static final long serialVersionUID = 1L;
        final String type;
        final String sourceFactionId;
        final String targetFactionId;
        final Object data;

        PendingEvent(String type, String sourceFactionId, String targetFactionId, Object data) {
            this.type = type;
            this.sourceFactionId = sourceFactionId;
            this.targetFactionId = targetFactionId;
            this.data = data;
        }
    }
}
