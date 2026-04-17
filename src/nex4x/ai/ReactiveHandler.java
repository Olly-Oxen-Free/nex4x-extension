package nex4x.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import nex4x.managers.Nex4xManager;
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

        log.info("[Nex4x] ReactiveHandler: " + eventType
                + " (" + sourceFactionId + " -> " + targetFactionId + ")"
                + " class=" + classification);

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
        log.info("[Nex4x] Military state change: " + eventType
                + " " + sourceFactionId + " -> " + targetFactionId);
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
