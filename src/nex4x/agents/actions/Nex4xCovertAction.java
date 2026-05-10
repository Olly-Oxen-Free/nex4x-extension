package nex4x.agents.actions;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.CovertActionIntel;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.Nex4xAgentManager;

import java.awt.Color;
import java.util.Map;

/**
 * Base class for nex4x-introduced covert actions that plug into Nex's CovertOpsManager
 * lifecycle. Concrete subclasses just declare the def id and override the two effect
 * callbacks; UI/scheduling/detection are owned by Nex.
 *
 * Companion-data (Nex4xAgentData) updates run inside onSuccess/onFailure so they fire
 * regardless of who initiated the action (player or AI).
 */
public abstract class Nex4xCovertAction extends CovertActionIntel {

    public Nex4xCovertAction() { super(); }

    public Nex4xCovertAction(AgentIntel agent, MarketAPI market,
                             FactionAPI agentFaction, FactionAPI targetFaction,
                             boolean playerInvolved, Map<String, Object> params) {
        super(agent, market, agentFaction, targetFaction, playerInvolved, params);
    }

    /** Action-specific success effect. data may be null if companion record absent. */
    protected abstract void applyNex4xEffect(Nex4xAgentData data, MarketAPI market);

    /** Action-specific detection fallout. */
    protected abstract void applyNex4xFallout(Nex4xAgentData data, MarketAPI market);

    /** Display label used by addCurrentActionPara/Bullet. Default: getDef().name. */
    protected String getActionLabel() {
        return getDef() != null && getDef().name != null ? getDef().name : getDefId();
    }

    private Nex4xAgentData getCompanion() {
        if (agent == null || agent.getAgent() == null) return null;
        Nex4xAgentManager mgr = Nex4xAgentManager.get();
        if (mgr == null) return null;
        return mgr.get(agent.getAgent().getId());
    }

    @Override
    public void onSuccess() {
        try {
            applyNex4xEffect(getCompanion(), market);
        } catch (Throwable ignore) { /* never let nex4x effect break Nex action lifecycle */ }
    }

    @Override
    public void onFailure() {
        try {
            applyNex4xFallout(getCompanion(), market);
        } catch (Throwable ignore) { /* same — Nex owns the resolve flow */ }
    }

    @Override
    public void addCurrentActionPara(TooltipMakerAPI info, float pad) {
        info.addPara(getActionLabel() + " in progress.", pad);
    }

    @Override
    public void addCurrentActionBullet(TooltipMakerAPI info, Color color, float pad) {
        info.addPara(getActionLabel(), color, pad);
    }

    /** Resolve player faction id once for Nex/nex4x callers that don't hold it. */
    protected String getPlayerFactionId() {
        return Misc.getCommissionFactionId() != null
                ? Misc.getCommissionFactionId()
                : com.fs.starfarer.api.Global.getSector().getPlayerFaction().getId();
    }

    /** Convenience: actor (agent) faction id. */
    protected String getActorFactionId() {
        return agentFaction != null ? agentFaction.getId() : null;
    }

    /** Convenience: market faction id (action target market's owner). */
    protected String getMarketFactionId() {
        return market != null ? market.getFactionId() : null;
    }
}
