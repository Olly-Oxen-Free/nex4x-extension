package nex4x.agents.actions.covert;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionConfig;
import nex4x.agents.actions.BaseAgentAction;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;

/** Disrupts the first non-structure industry. Detection injects grievance pressure. */
public class SabotageIndustryAction extends BaseAgentAction {
    private static final long serialVersionUID = 1L;

    public static final float DISRUPTION_DAYS = 30f;

    public SabotageIndustryAction(String agentId, String actorFactionId, String marketId,
                                  ActionConfig config, int agentLevel) {
        super(agentId, actorFactionId, marketId, config, agentLevel);
    }

    @Override
    protected void applyEffect(Nex4xAgentData data, MarketAPI market) {
        Industry target = null;
        for (Industry i : market.getIndustries()) {
            if (i.isStructure()) continue;
            if (i.isDisrupted()) continue;
            target = i;
            break;
        }
        if (target != null) {
            target.setDisrupted(DISRUPTION_DAYS + target.getDisruptedDays());
            log.info("[Nex4x] Sabotaged " + target.getCurrentName() + " on " + market.getName());
        }
    }

    @Override
    protected void applyDetectionFallout(Nex4xAgentData data, MarketAPI market) {
        PressureManager.getOrCreate().applyEvent(
                market.getFactionId(), actorFactionId, PressureSource.GRIEVANCE, 25f);
        data.getBuildupTracker().damageByOneLevel();
    }
}
