package nex4x.agents.actions.guerrilla;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionDefIds;
import nex4x.agents.actions.Nex4xCovertAction;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;

/** Incite local irregulars to raid the host market. Stability hit + military pressure on host. */
public class InciteRaidAction extends Nex4xCovertAction {

    public static final String STABILITY_MOD_ID = "nex4x_incited_raid";

    public InciteRaidAction() {}

    @Override public String getDefId() { return ActionDefIds.GUERRILLA_INCITE_RAID; }

    @Override
    protected void applyNex4xEffect(Nex4xAgentData data, MarketAPI market) {
        if (market != null) {
            market.getStability().modifyFlat(STABILITY_MOD_ID, -2, "Nex4x: Incited Raid");
        }
        String actor = getActorFactionId();
        String host = getMarketFactionId();
        if (actor != null && host != null) {
            PressureManager.getOrCreate().applyEvent(actor, host,
                    PressureSource.MILITARY, 10f);
        }
    }

    @Override
    protected void applyNex4xFallout(Nex4xAgentData data, MarketAPI market) {
        String actor = getActorFactionId();
        String host = getMarketFactionId();
        if (actor != null && host != null) {
            PressureManager.getOrCreate().applyEvent(host, actor,
                    PressureSource.GRIEVANCE, 30f);
        }
        if (data != null) data.getBuildupTracker().damageByOneLevel();
    }
}
