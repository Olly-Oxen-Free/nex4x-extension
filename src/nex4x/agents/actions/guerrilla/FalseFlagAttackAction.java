package nex4x.agents.actions.guerrilla;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionDefIds;
import nex4x.agents.actions.Nex4xCovertAction;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;

/**
 * Stage an attack pretending to be a third faction. Success: stability hit on host
 * + grievance pressure on the framed faction. Detection: double grievance lands on actor
 * AND framed faction now resents the actor too.
 *
 * Framed faction id is read from {@link CovertActionIntel#thirdFaction} (set by Nex's
 * AgentOrdersDialog via the params map; key {@code "thirdFaction"} expected).
 */
public class FalseFlagAttackAction extends Nex4xCovertAction {

    public FalseFlagAttackAction() {}

    @Override public String getDefId() { return ActionDefIds.GUERRILLA_FALSE_FLAG; }

    private String framedFactionId() {
        return thirdFaction != null ? thirdFaction.getId() : null;
    }

    @Override
    protected void applyNex4xEffect(Nex4xAgentData data, MarketAPI market) {
        if (market == null) return;
        market.getStability().modifyFlat("nex4x_false_flag", -2, "Nex4x: False Flag Strike");
        String host = getMarketFactionId();
        String framed = framedFactionId();
        if (host != null && framed != null) {
            PressureManager.getOrCreate().applyEvent(host, framed,
                    PressureSource.GRIEVANCE, 50f);
        }
    }

    @Override
    protected void applyNex4xFallout(Nex4xAgentData data, MarketAPI market) {
        String host = getMarketFactionId();
        String actor = getActorFactionId();
        String framed = framedFactionId();
        if (host != null && actor != null) {
            PressureManager.getOrCreate().applyEvent(host, actor,
                    PressureSource.GRIEVANCE, 100f);
        }
        if (framed != null && actor != null) {
            PressureManager.getOrCreate().applyEvent(framed, actor,
                    PressureSource.GRIEVANCE, 60f);
        }
        if (data != null) data.getBuildupTracker().reset();
    }
}
