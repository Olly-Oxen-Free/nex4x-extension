package nex4x.agents.actions.covert;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionConfig;
import nex4x.agents.actions.BaseAgentAction;

/** Accelerates buildup; on detection, cuts buildup by a level. */
public class EmbedAction extends BaseAgentAction {
    private static final long serialVersionUID = 1L;

    public EmbedAction(String agentId, String actorFactionId, String marketId,
                       ActionConfig config, int agentLevel) {
        super(agentId, actorFactionId, marketId, config, agentLevel);
    }

    @Override
    protected void applyEffect(Nex4xAgentData data, MarketAPI market) {
        data.getBuildupTracker().addBoost(30f);
    }

    @Override
    protected void applyDetectionFallout(Nex4xAgentData data, MarketAPI market) {
        data.getBuildupTracker().damageByOneLevel();
    }
}
