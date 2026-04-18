package nex4x.agents.actions.guerrilla;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionConfig;
import nex4x.agents.actions.BaseAgentAction;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;

/**
 * Frame a third-party faction. On success, grievance pressure from victim onto
 * framed — not actor. On detection, actor eats the full cost twice over.
 */
public class FalseFlagAttackAction extends BaseAgentAction {
    private static final long serialVersionUID = 1L;

    private final String framedFactionId;

    public FalseFlagAttackAction(String agentId, String actorFactionId, String marketId,
                                 String framedFactionId,
                                 ActionConfig config, int agentLevel) {
        super(agentId, actorFactionId, marketId, config, agentLevel);
        this.framedFactionId = framedFactionId;
    }

    public String getFramedFactionId() { return framedFactionId; }

    @Override
    protected void applyEffect(Nex4xAgentData data, MarketAPI market) {
        // Stability hit + grievance pressure from victim onto framed faction
        market.getStability().modifyFlat("nex4x_false_flag", -2, "Nex4x: False Flag Strike");
        PressureManager.getOrCreate().applyEvent(
                market.getFactionId(), framedFactionId, PressureSource.GRIEVANCE, 50f);
    }

    @Override
    protected void applyDetectionFallout(Nex4xAgentData data, MarketAPI market) {
        // Revealed: double grievance onto actor + framed faction dislikes actor too
        PressureManager.getOrCreate().applyEvent(
                market.getFactionId(), actorFactionId, PressureSource.GRIEVANCE, 100f);
        PressureManager.getOrCreate().applyEvent(
                framedFactionId, actorFactionId, PressureSource.GRIEVANCE, 60f);
        data.getBuildupTracker().reset();
    }
}
