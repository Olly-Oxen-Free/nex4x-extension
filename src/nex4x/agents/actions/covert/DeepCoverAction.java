package nex4x.agents.actions.covert;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.actions.ActionDefIds;
import nex4x.agents.actions.Nex4xCovertAction;

/** Saboteur burrows for a temporary detection-immunity window; failure burns the cover. */
public class DeepCoverAction extends Nex4xCovertAction {
    public static final float PROTECTION_DAYS = 60f;

    public DeepCoverAction() {}

    @Override public String getDefId() { return ActionDefIds.DEEP_COVER; }

    @Override
    protected void applyNex4xEffect(Nex4xAgentData data, MarketAPI market) {
        if (data == null) return;
        data.activateSynergy(PROTECTION_DAYS);
        data.getBuildupTracker().addBoost(20f);
    }

    @Override
    protected void applyNex4xFallout(Nex4xAgentData data, MarketAPI market) {
        if (data != null) data.getBuildupTracker().reset();
    }
}
