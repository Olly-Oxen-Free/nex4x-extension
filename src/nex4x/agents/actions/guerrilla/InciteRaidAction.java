package nex4x.agents.actions.guerrilla;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionConfig;
import nex4x.agents.actions.BaseAgentAction;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;

/** Incites a raid — reduces target stability, pressures aggressor. */
public class InciteRaidAction extends BaseAgentAction {
    private static final long serialVersionUID = 1L;
    private static final String STABILITY_MOD_ID = "nex4x_incited_raid";

    public InciteRaidAction(String agentId, String actorFactionId, String marketId,
                            ActionConfig config, int agentLevel) {
        super(agentId, actorFactionId, marketId, config, agentLevel);
    }

    @Override
    protected void applyEffect(Nex4xAgentData data, MarketAPI market) {
        market.getStability().modifyFlat(STABILITY_MOD_ID, -2, "Nex4x: Incited Raid");
        PressureManager.getOrCreate().applyEvent(
                actorFactionId, market.getFactionId(), PressureSource.MILITARY, 10f);
    }

    @Override
    protected void applyDetectionFallout(Nex4xAgentData data, MarketAPI market) {
        PressureManager.getOrCreate().applyEvent(
                market.getFactionId(), actorFactionId, PressureSource.GRIEVANCE, 30f);
        data.getBuildupTracker().damageByOneLevel();
    }
}
