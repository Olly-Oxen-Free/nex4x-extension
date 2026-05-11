package nex4x.integration;

import com.fs.starfarer.api.Global;
import nex4x.managers.Nex4xManager;
import org.apache.log4j.Logger;

/**
 * Intercepts DiplomacyBrain war/peace decisions and vetoes them
 * when our Diplomatic Executor is active.
 *
 * Registered in onGameLoad. If Nex4xManager is null (mod failed init),
 * this listener is never registered and DiplomacyBrain runs stock.
 */
public class Nex4xDiplomacyBrainListener {

    private static final Logger log = Global.getLogger(Nex4xDiplomacyBrainListener.class);

    /**
     * Called when DiplomacyBrain wants to fire a diplomacy event.
     * Returns true to veto, false to allow.
     */
    public boolean reportDiplomacyEvent(String eventType,
                                         String factionId, String targetFactionId) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return false;

        // Veto war declarations and peace proposals — our executor handles these
        if ("declare_war".equals(eventType) || "make_peace".equals(eventType)
                || "ceasefire".equals(eventType)) {
            log.info("[Nex4x] Vetoed DiplomacyBrain " + eventType
                    + " from " + factionId + " -> " + targetFactionId
                    + " (handled by Diplomatic Executor)");
            return true;
        }

        return false;
    }
}
