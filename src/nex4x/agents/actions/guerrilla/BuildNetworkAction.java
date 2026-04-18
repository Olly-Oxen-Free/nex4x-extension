package nex4x.agents.actions.guerrilla;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionConfig;
import nex4x.agents.actions.BaseAgentAction;

/** Network-tier: builds guerrilla standing; detection resets buildup by 1 level. */
public class BuildNetworkAction extends BaseAgentAction {
    private static final long serialVersionUID = 1L;

    public BuildNetworkAction(String agentId, String actorFactionId, String marketId,
                              ActionConfig config, int agentLevel) {
        super(agentId, actorFactionId, marketId, config, agentLevel);
    }

    @Override
    protected void applyEffect(Nex4xAgentData data, MarketAPI market) {
        data.getBuildupTracker().addBoost(25f);
    }

    @Override
    protected void applyDetectionFallout(Nex4xAgentData data, MarketAPI market) {
        data.getBuildupTracker().damageByOneLevel();
    }
}
