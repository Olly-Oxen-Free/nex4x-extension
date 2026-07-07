package nex4x.ui.viceroy;

import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Minimal intel-journal entry created when a player purchases intel from a viceroy.
 * Tier: LOCATION_TIP, FACTION_GOALS, or BOUNTY_TARGETS.
 *
 * Extends BaseIntelPlugin (no abstract methods) rather than BaseMissionIntel
 * to avoid implementing the full mission lifecycle.
 */
public class ViceroyIntelItem extends BaseIntelPlugin {

    private final IntelPurchaseHandler.IntelTier tier;
    private final String marketFactionId;

    public ViceroyIntelItem(IntelPurchaseHandler.IntelTier tier, String marketFactionId) {
        this.tier = tier;
        this.marketFactionId = marketFactionId;
    }

    public IntelPurchaseHandler.IntelTier getTier() { return tier; }
    public String getMarketFactionId() { return marketFactionId; }

    @Override
    protected String getName() {
        return tier.displayName + " (" + marketFactionId + ")";
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        String body;
        switch (tier) {
            case LOCATION_TIP:
                body = "A discreet tip about a system of interest in "
                        + marketFactionId + "'s sphere.";
                break;
            case FACTION_GOALS:
                body = "Summarised strategic priorities of "
                        + marketFactionId + " as of this cycle.";
                break;
            case BOUNTY_TARGETS:
                body = "Current bounty roster maintained by "
                        + marketFactionId + ".";
                break;
            default:
                body = "Purchased intelligence.";
        }
        info.addPara(body, 0f);
    }
}
