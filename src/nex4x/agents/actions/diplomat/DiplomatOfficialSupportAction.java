package nex4x.agents.actions.diplomat;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionDefIds;
import nex4x.agents.actions.Nex4xCovertAction;
import nex4x.influence.InfluenceManager;
import nex4x.influence.InfluenceSource;

/** Negotiator publicly assists host; gains influence for actor faction. Low-detection by design. */
public class DiplomatOfficialSupportAction extends Nex4xCovertAction {

    public DiplomatOfficialSupportAction() {}

    @Override public String getDefId() { return ActionDefIds.DIPLOMAT_OFFICIAL; }

    @Override
    protected void applyNex4xEffect(Nex4xAgentData data, MarketAPI market) {
        String actor = getActorFactionId();
        int level = data != null ? data.getBuildupTracker().getLevel() : 0;
        if (data != null) data.getBuildupTracker().addBoost(15f);
        if (actor != null) {
            float infGain = 3f + 2f * level;
            InfluenceManager.getOrCreate().addLump(actor, infGain, InfluenceSource.AGENT_ACTION);
        }
    }

    @Override
    protected void applyNex4xFallout(Nex4xAgentData data, MarketAPI market) {
        // Official actions don't suffer "detection" — Nex still records the failure roll.
    }
}
