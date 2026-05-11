package nex4x.strategic.action;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.action.BaseStrategicAction;
import exerelin.campaign.ai.concern.StrategicConcern;
import nex4x.ai.goals.GoalType;
import nex4x.strategic.concern.GoalWrapConcern;
import org.apache.log4j.Logger;

/**
 * Strategic action: sue for peace with a target faction.
 * Only triggers from a GoalWrapConcern whose goal type is END_WAR.
 */
public class SeekPeaceFromWillingnessAction extends BaseStrategicAction {

    private static final Logger log = Global.getLogger(SeekPeaceFromWillingnessAction.class);

    @Override
    public boolean generate() {
        if (concern == null || ai == null) return false;
        if (!(concern instanceof GoalWrapConcern)) return false;
        GoalWrapConcern g = (GoalWrapConcern) concern;
        if (g.getSourceGoal() == null) return false;
        if (g.getSourceGoal().type != GoalType.END_WAR) return false;

        String targetId = g.getSourceGoal().targetFactionId;
        if (targetId == null) return false;

        FactionAPI us = Global.getSector().getFaction(ai.getFactionId());
        FactionAPI them = Global.getSector().getFaction(targetId);
        if (us == null || them == null) return false;
        if (!us.isHostileTo(them)) return false;

        try {
            DiplomacyManager.createDiplomacyEvent(us, them, "peace_treaty", null);
            log.info("[Nex4x] " + us.getId() + " SEEKS PEACE with " + targetId
                    + " via StrategicAction");
            return true;
        } catch (Exception e) {
            log.error("[Nex4x] Failed to propose peace: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean canUse(StrategicConcern c) {
        if (!(c instanceof GoalWrapConcern)) return false;
        GoalWrapConcern g = (GoalWrapConcern) c;
        return g.getSourceGoal() != null && g.getSourceGoal().type == GoalType.END_WAR;
    }
}
