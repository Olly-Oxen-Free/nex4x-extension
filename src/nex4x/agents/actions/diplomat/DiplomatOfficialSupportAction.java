package nex4x.agents.actions.diplomat;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionConfig;
import nex4x.agents.actions.BaseAgentAction;
import nex4x.influence.InfluenceManager;
import nex4x.influence.InfluenceSource;

/**
 * Official diplomat action — public support gesture. No detection penalty
 * (it's official); buildup tracker becomes "standing" with host market.
 */
public class DiplomatOfficialSupportAction extends BaseAgentAction {
    private static final long serialVersionUID = 1L;

    public DiplomatOfficialSupportAction(String agentId, String actorFactionId, String marketId,
                                         ActionConfig config, int agentLevel) {
        super(agentId, actorFactionId, marketId, config, agentLevel);
    }

    @Override
    protected void applyEffect(Nex4xAgentData data, MarketAPI market) {
        data.getBuildupTracker().addBoost(15f);
        float infGain = 3f + 2f * data.getBuildupTracker().getLevel();
        InfluenceManager.getOrCreate().addLump(actorFactionId, infGain, InfluenceSource.AGENT_ACTION);
    }

    @Override
    protected void applyDetectionFallout(Nex4xAgentData data, MarketAPI market) {
        // Official actions don't suffer "detection" in the covert sense.
    }
}
