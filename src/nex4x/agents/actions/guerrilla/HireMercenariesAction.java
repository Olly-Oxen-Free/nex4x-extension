package nex4x.agents.actions.guerrilla;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionConfig;
import nex4x.agents.actions.BaseAgentAction;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;

/** High-tier: sustained mercenary harassment applies ongoing military pressure. */
public class HireMercenariesAction extends BaseAgentAction {
    private static final long serialVersionUID = 1L;

    public HireMercenariesAction(String agentId, String actorFactionId, String marketId,
                                 ActionConfig config, int agentLevel) {
        super(agentId, actorFactionId, marketId, config, agentLevel);
    }

    @Override
    protected void applyEffect(Nex4xAgentData data, MarketAPI market) {
        PressureManager.getOrCreate().applyEvent(
                actorFactionId, market.getFactionId(), PressureSource.MILITARY, 25f);
        market.getStability().modifyFlat("nex4x_merc_pressure", -1, "Nex4x: Merc Pressure");
    }

    @Override
    protected void applyDetectionFallout(Nex4xAgentData data, MarketAPI market) {
        PressureManager.getOrCreate().applyEvent(
                market.getFactionId(), actorFactionId, PressureSource.GRIEVANCE, 40f);
        data.getBuildupTracker().damageByOneLevel();
    }
}
