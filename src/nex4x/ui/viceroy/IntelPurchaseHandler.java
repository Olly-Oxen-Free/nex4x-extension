package nex4x.ui.viceroy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.Nex4xAgentManager;

public class IntelPurchaseHandler {
    public static final int PRICE_LOCATION_TIP = 500;
    public static final int PRICE_FACTION_GOALS = 2500;
    public static final int PRICE_BOUNTY_TARGETS = 1000;

    public static void open(InteractionDialogAPI dialog, MarketAPI market) {
        dialog.getTextPanel().addPara("Intel on offer:");
        dialog.getTextPanel().addPara(" • System location tip — "
                + PRICE_LOCATION_TIP + " credits");
        dialog.getTextPanel().addPara(" • Current strategic goals of " + market.getFaction().getDisplayName()
                + " — " + PRICE_FACTION_GOALS + " credits");
        dialog.getTextPanel().addPara(" • Active bounty roster — "
                + PRICE_BOUNTY_TARGETS + " credits");
        dialog.getTextPanel().addPara("(purchase wiring — submenu extension in Phase 12)");
    }
}
