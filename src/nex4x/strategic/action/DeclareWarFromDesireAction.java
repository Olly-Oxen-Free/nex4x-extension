package nex4x.strategic.action;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.action.BaseStrategicAction;
import exerelin.campaign.ai.concern.StrategicConcern;
import nex4x.casusbelli.CasusBelli;
import nex4x.integration.NexDiplomacyBridge;
import nex4x.managers.Nex4xManager;
import nex4x.strategic.concern.BadgeProvocationConcern;
import nex4x.strategic.concern.GoalWrapConcern;
import org.apache.log4j.Logger;

/**
 * Strategic action: declare war on target faction.
 * Target resolved from wrapping concern (GoalWrapConcern or BadgeProvocationConcern).
 * Delegates to exerelin DiplomacyManager to perform the actual declaration
 * so Nex event log / diplomacy UI reflects it correctly.
 */
public class DeclareWarFromDesireAction extends BaseStrategicAction {

    private static final Logger log = Global.getLogger(DeclareWarFromDesireAction.class);

    @Override
    public boolean generate() {
        if (concern == null || ai == null) return false;
        String targetId = resolveTarget(concern);
        if (targetId == null) return false;

        FactionAPI us = Global.getSector().getFaction(ai.getFactionId());
        FactionAPI them = Global.getSector().getFaction(targetId);
        if (us == null || them == null) return false;
        if (us.isHostileTo(them)) return false;

        try {
            Nex4xManager mgr = Nex4xManager.getManager();
            CasusBelli cb = (mgr != null)
                    ? mgr.getCasusBelliManager().getActiveCasusBelliFor(us.getId(), targetId)
                    : null;
            if (cb != null) {
                NexDiplomacyBridge.fireJustifiedWar(us, them, cb.getType().name());
            } else {
                DiplomacyManager.createDiplomacyEvent(us, them, "declare_war", null);
            }
            log.info("[Nex4x] " + us.getId() + " DECLARES WAR on " + targetId
                    + " via StrategicAction" + (cb != null ? " [CB: " + cb.getType().name() + "]" : ""));
            return true;
        } catch (Exception e) {
            log.error("[Nex4x] Failed to declare war: " + e.getMessage());
            return false;
        }
    }

    private String resolveTarget(StrategicConcern c) {
        if (c instanceof GoalWrapConcern) {
            GoalWrapConcern g = (GoalWrapConcern) c;
            return g.getSourceGoal() != null ? g.getSourceGoal().targetFactionId : null;
        }
        if (c instanceof BadgeProvocationConcern) {
            return ((BadgeProvocationConcern) c).getProvocatorFactionId();
        }
        if (c.getFaction() != null) return c.getFaction().getId();
        return null;
    }

    @Override
    public boolean canUse(StrategicConcern c) {
        return resolveTarget(c) != null;
    }
}
