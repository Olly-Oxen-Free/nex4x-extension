package nex4x.campaign;

import com.fs.starfarer.api.campaign.BaseCampaignPlugin;

/**
 * Not strictly needed if we wire through FactionBrowserIntel's Contact Leader button.
 * This plugin is a future hook for rules.csv integration -- keep a thin stub for v5.
 * Register in v5.1 when rules.csv wiring is added.
 */
public class CapitalInteractionPlugin extends BaseCampaignPlugin {
    public String getId() { return "nex4x_capital_interaction"; }
    public boolean isTransient() { return true; }
}
