package nex4x.strategic.action;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.ai.action.BaseStrategicAction;
import exerelin.campaign.ai.concern.StrategicConcern;
import nex4x.agreements.AgreementManager;
import nex4x.agreements.AgreementType;
import nex4x.managers.Nex4xManager;
import nex4x.strategic.concern.GoalWrapConcern;
import org.apache.log4j.Logger;

/**
 * Strategic action: propose next-tier agreement (NAP → Defensive Pact → Alliance)
 * with the target of an alliance-building goal.
 */
public class ProposeTieredAgreementAction extends BaseStrategicAction {

    private static final Logger log = Global.getLogger(ProposeTieredAgreementAction.class);

    @Override
    public boolean generate() {
        if (concern == null || ai == null) return false;
        if (!(concern instanceof GoalWrapConcern)) return false;
        GoalWrapConcern gc = (GoalWrapConcern) concern;
        if (gc.getSourceGoal() == null) return false;

        String myId = ai.getFactionId();
        String targetId = gc.getSourceGoal().targetFactionId;
        if (targetId == null) return false;

        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return false;
        AgreementManager am = mgr.getAgreementManager();
        AgreementType current = am.getAllianceTier(myId, targetId);
        AgreementType next = current.getNextAllianceTier();
        if (next == null) return false;
        if (!am.canPropose(myId, targetId, next)) return false;

        FactionAPI us = Global.getSector().getFaction(myId);
        FactionAPI them = Global.getSector().getFaction(targetId);
        if (us == null || them == null) return false;
        if (us.isHostileTo(them)) return false;

        log.info("[Nex4x] " + myId + " proposes " + next.displayName + " to " + targetId
                + " via StrategicAction");
        return true;
    }

    @Override
    public boolean canUse(StrategicConcern c) {
        if (!(c instanceof GoalWrapConcern)) return false;
        GoalWrapConcern g = (GoalWrapConcern) c;
        if (g.getSourceGoal() == null || g.getSourceGoal().targetFactionId == null) return false;

        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null || ai == null) return false;
        AgreementType cur = mgr.getAgreementManager()
                .getAllianceTier(ai.getFactionId(), g.getSourceGoal().targetFactionId);
        return cur.getNextAllianceTier() != null;
    }
}
