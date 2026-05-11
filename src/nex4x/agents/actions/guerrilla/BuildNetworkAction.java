package nex4x.agents.actions.guerrilla;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionDefIds;
import nex4x.agents.actions.Nex4xCovertAction;

/** Guerrilla builds local network — heavy buildup boost. Detection: lose a level. */
public class BuildNetworkAction extends Nex4xCovertAction {

    public BuildNetworkAction() {}

    @Override public String getDefId() { return ActionDefIds.GUERRILLA_BUILD_NETWORK; }

    @Override
    protected void applyNex4xEffect(Nex4xAgentData data, MarketAPI market) {
        if (data != null) data.getBuildupTracker().addBoost(25f);
    }

    @Override
    protected void applyNex4xFallout(Nex4xAgentData data, MarketAPI market) {
        if (data != null) data.getBuildupTracker().damageByOneLevel();
    }
}
