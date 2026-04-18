package nex4x.agents.actions.covert;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionConfig;
import nex4x.agents.actions.BaseAgentAction;
import nex4x.influence.InfluenceManager;
import nex4x.influence.InfluenceSource;

/** Grants influence income + buildup on success. On detection, small rep penalty. */
public class IntelGatherAction extends BaseAgentAction {
    private static final long serialVersionUID = 1L;

    public IntelGatherAction(String agentId, String actorFactionId, String marketId,
                             ActionConfig config, int agentLevel) {
        super(agentId, actorFactionId, marketId, config, agentLevel);
    }

    @Override
    protected void applyEffect(Nex4xAgentData data, MarketAPI market) {
        InfluenceManager.getOrCreate().addLump(actorFactionId,
                5f + 2f * data.getBuildupTracker().getLevel(),
                InfluenceSource.AGENT_ACTION);
        data.getBuildupTracker().addBoost(10f);
    }

    @Override
    protected void applyDetectionFallout(Nex4xAgentData data, MarketAPI market) {
        data.getBuildupTracker().damageByOneLevel();
    }
}
