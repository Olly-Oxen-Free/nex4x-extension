package nex4x.agents.actions.diplomat;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.Nex4xAgentManager;
import nex4x.agents.actions.ActionConfig;
import nex4x.agents.actions.BaseAgentAction;
import nex4x.agents.diplomat.DiplomatExpulsionCascade;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;

/**
 * Off-the-books diplomat action. On detection triggers expulsion cascade:
 * this agent PNG'd locally; all same-faction diplomats on target faction's
 * territory take cascade suspicion (buildup -1 level).
 */
public class DiplomatUnofficialLeakAction extends BaseAgentAction {
    private static final long serialVersionUID = 1L;

    public static final float EXPULSION_COOLDOWN_DAYS = 180f;
    public static final float CASCADE_SUSPICION_DAYS = 90f;

    public DiplomatUnofficialLeakAction(String agentId, String actorFactionId, String marketId,
                                        ActionConfig config, int agentLevel) {
        super(agentId, actorFactionId, marketId, config, agentLevel);
    }

    @Override
    protected void applyEffect(Nex4xAgentData data, MarketAPI market) {
        PressureManager.getOrCreate().applyEvent(
                actorFactionId, market.getFactionId(), PressureSource.AGENT_ACTION, 20f);
    }

    @Override
    protected void applyDetectionFallout(Nex4xAgentData data, MarketAPI market) {
        // PNG locally
        data.setExpulsion(targetMarketId, EXPULSION_COOLDOWN_DAYS);
        PressureManager.getOrCreate().applyEvent(
                market.getFactionId(), actorFactionId, PressureSource.GRIEVANCE, 40f);
        DiplomatExpulsionCascade.apply(
                Nex4xAgentManager.getOrCreate(), actorFactionId, market.getFactionId(),
                CASCADE_SUSPICION_DAYS);
    }
}
