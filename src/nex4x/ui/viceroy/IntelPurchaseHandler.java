package nex4x.ui.viceroy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.util.MutableValue;

/**
 * Handles player purchase of intelligence tips from a viceroy.
 * Task 21b: real credit deduction + intel item grant.
 */
public class IntelPurchaseHandler {

    public enum IntelTier {
        LOCATION_TIP("System Location Tip", 500),
        FACTION_GOALS("Faction Strategic Goals", 2500),
        BOUNTY_TARGETS("Active Bounty Roster", 1000);

        public final String displayName;
        public final int price;

        IntelTier(String displayName, int price) {
            this.displayName = displayName;
            this.price = price;
        }
    }

    // Legacy price constants for smoke-test compatibility.
    public static final int PRICE_LOCATION_TIP    = IntelTier.LOCATION_TIP.price;
    public static final int PRICE_FACTION_GOALS   = IntelTier.FACTION_GOALS.price;
    public static final int PRICE_BOUNTY_TARGETS  = IntelTier.BOUNTY_TARGETS.price;

    /** Called from ViceroyDialog main menu to print the offering text. */
    public static void open(InteractionDialogAPI dialog, MarketAPI market) {
        TextPanelAPI text = dialog.getTextPanel();
        text.addPara("Intel on offer from " + market.getFaction().getDisplayName() + ":");
        text.addPara(" • " + IntelTier.LOCATION_TIP.displayName
                + " — " + IntelTier.LOCATION_TIP.price + " credits");
        text.addPara(" • " + IntelTier.FACTION_GOALS.displayName
                + " — " + IntelTier.FACTION_GOALS.price + " credits");
        text.addPara(" • " + IntelTier.BOUNTY_TARGETS.displayName
                + " — " + IntelTier.BOUNTY_TARGETS.price + " credits");
    }

    /**
     * Deducts credits and grants an intel-journal entry.
     * Called from ViceroyDialog after the player selects a tier.
     */
    public static void purchase(InteractionDialogAPI dialog, MarketAPI market, IntelTier tier) {
        TextPanelAPI text = dialog != null ? dialog.getTextPanel() : null;
        MutableValue credits = Global.getSector().getPlayerFleet().getCargo().getCredits();

        if (credits.get() < tier.price) {
            if (text != null) {
                text.addPara("Insufficient credits. " + tier.displayName
                        + " costs " + tier.price + " credits.");
            }
            return;
        }

        credits.subtract(tier.price);
        AddRemoveCommodity.addCreditsLossText(tier.price, text);

        ViceroyIntelItem item = new ViceroyIntelItem(tier, market.getFactionId());
        Global.getSector().getIntelManager().addIntel(item, false, text);

        if (text != null) {
            text.addPara("Purchased: " + tier.displayName
                    + ". Intel added to your journal.");
        }
    }
}
