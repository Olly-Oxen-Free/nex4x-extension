package nex4x.agents.actions.diplomat;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.Nex4xAgentManager;
import nex4x.agents.actions.ActionDefIds;
import nex4x.agents.actions.Nex4xCovertAction;
import nex4x.agents.diplomat.DiplomatExpulsionCascade;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;

/** Negotiator leaks intel; success injects pressure on host. Detection: PNG + cascade. */
public class DiplomatUnofficialLeakAction extends Nex4xCovertAction {

    public static final float EXPULSION_COOLDOWN_DAYS  = 90f;
    public static final float CASCADE_SUSPICION_DAYS   = 60f;

    public DiplomatUnofficialLeakAction() {}

    @Override public String getDefId() { return ActionDefIds.DIPLOMAT_LEAK; }

    @Override
    protected void applyNex4xEffect(Nex4xAgentData data, MarketAPI market) {
        String actor = getActorFactionId();
        String host  = getMarketFactionId();
        if (actor != null && host != null) {
            PressureManager.getOrCreate().applyEvent(actor, host,
                    PressureSource.AGENT_ACTION, 20f);
        }
    }

    @Override
    protected void applyNex4xFallout(Nex4xAgentData data, MarketAPI market) {
        String actor = getActorFactionId();
        String host  = getMarketFactionId();
        if (data != null && market != null) {
            data.setExpulsion(market.getId(), EXPULSION_COOLDOWN_DAYS);
        }
        if (actor != null && host != null) {
            PressureManager.getOrCreate().applyEvent(host, actor,
                    PressureSource.GRIEVANCE, 40f);
            DiplomatExpulsionCascade.apply(
                    Nex4xAgentManager.getOrCreate(), actor, host, CASCADE_SUSPICION_DAYS);
        }
    }
}
