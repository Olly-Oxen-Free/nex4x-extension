package nex4x.agents.actions.guerrilla;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionDefIds;
import nex4x.agents.actions.Nex4xCovertAction;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;

/** Hire mercenaries to harass host: stability dip + military pressure. Detection backfire. */
public class HireMercenariesAction extends Nex4xCovertAction {

    public HireMercenariesAction() {}

    @Override public String getDefId() { return ActionDefIds.GUERRILLA_HIRE_MERCS; }

    @Override
    protected void applyNex4xEffect(Nex4xAgentData data, MarketAPI market) {
        String actor = getActorFactionId();
        String host = getMarketFactionId();
        if (actor != null && host != null) {
            PressureManager.getOrCreate().applyEvent(actor, host,
                    PressureSource.MILITARY, 25f);
        }
        if (market != null) {
            market.getStability().modifyFlat("nex4x_merc_pressure", -1, "Nex4x: Merc Pressure");
        }
    }

    @Override
    protected void applyNex4xFallout(Nex4xAgentData data, MarketAPI market) {
        String actor = getActorFactionId();
        String host = getMarketFactionId();
        if (actor != null && host != null) {
            PressureManager.getOrCreate().applyEvent(host, actor,
                    PressureSource.GRIEVANCE, 40f);
        }
        if (data != null) data.getBuildupTracker().damageByOneLevel();
    }
}
