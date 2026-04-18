package nex4x.agents.actions.covert;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionConfig;
import nex4x.agents.actions.BaseAgentAction;

/**
 * Tier-3 action: reduces detection vulnerability for an extended period.
 * Applied via a synergy flag; detection fallout is catastrophic.
 */
public class DeepCoverAction extends BaseAgentAction {
    private static final long serialVersionUID = 1L;

    public static final float PROTECTION_DAYS = 120f;

    public DeepCoverAction(String agentId, String actorFactionId, String marketId,
                           ActionConfig config, int agentLevel) {
        super(agentId, actorFactionId, marketId, config, agentLevel);
    }

    @Override
    protected void applyEffect(Nex4xAgentData data, MarketAPI market) {
        data.activateSynergy(PROTECTION_DAYS);
        data.getBuildupTracker().addBoost(20f);
    }

    @Override
    protected void applyDetectionFallout(Nex4xAgentData data, MarketAPI market) {
        data.getBuildupTracker().reset();
    }
}
