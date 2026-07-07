package nex4x.ui.viceroy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.util.MutableValue;
import nex4x.leaders.LeaderConfig;
import nex4x.leaders.LeaderConfigRegistry;

/**
 * Handles player purchase of AI cores from a viceroy.
 * Task 21c: real credit deduction + AI core to cargo.
 */
public class AiCoreHandler {

    public static final String ITEM_ALPHA = "alpha_core";
    public static final String ITEM_BETA  = "beta_core";
    public static final String ITEM_GAMMA = "gamma_core";

    public static final int PRICE_ALPHA = 150000;
    public static final int PRICE_BETA  = 50000;
    public static final int PRICE_GAMMA = 10000;

    /** Called from ViceroyDialog main menu; prints the offering text. */
    public static void open(InteractionDialogAPI dialog, MarketAPI market) {
        LeaderConfig cfg = LeaderConfigRegistry.get(market.getFactionId());
        if (!cfg.canSellAiCores) {
            dialog.getTextPanel().addPara("'We do not trade in such things,' says the "
                    + cfg.viceroyTitle + " with finality.");
            return;
        }
        TextPanelAPI text = dialog.getTextPanel();
        text.addPara("The " + cfg.viceroyTitle
                + " can arrange access to AI cores at premium rates:");
        text.addPara(" • Alpha Core — " + PRICE_ALPHA + " credits");
        text.addPara(" • Beta Core  — " + PRICE_BETA  + " credits");
        text.addPara(" • Gamma Core — " + PRICE_GAMMA + " credits");
    }

    /**
     * Deducts credits and adds the requested core to player cargo.
     * Called from ViceroyDialog after the player selects a tier.
     *
     * @param coreItemId  One of ITEM_ALPHA / ITEM_BETA / ITEM_GAMMA.
     */
    public static void purchase(InteractionDialogAPI dialog, MarketAPI market, String coreItemId) {
        LeaderConfig cfg = LeaderConfigRegistry.get(market.getFactionId());
        TextPanelAPI text = dialog != null ? dialog.getTextPanel() : null;

        if (!cfg.canSellAiCores) {
            if (text != null) {
                text.addPara("This faction does not deal in AI cores.");
            }
            return;
        }

        int price = priceFor(coreItemId);
        if (price < 0) {
            if (text != null) text.addPara("Unknown core type: " + coreItemId);
            return;
        }

        MutableValue credits = Global.getSector().getPlayerFleet().getCargo().getCredits();
        if (credits.get() < price) {
            if (text != null) {
                text.addPara("Insufficient credits. That core costs " + price + " credits.");
            }
            return;
        }

        credits.subtract(price);
        AddRemoveCommodity.addCreditsLossText(price, text);

        Global.getSector().getPlayerFleet().getCargo()
                .addSpecial(new SpecialItemData(coreItemId, null), 1f);

        String coreName = displayNameFor(coreItemId);
        if (text != null) {
            text.addPara("Acquired: " + coreName + ". It has been added to your cargo.");
        }
    }

    private static int priceFor(String id) {
        if (ITEM_ALPHA.equals(id)) return PRICE_ALPHA;
        if (ITEM_BETA.equals(id))  return PRICE_BETA;
        if (ITEM_GAMMA.equals(id)) return PRICE_GAMMA;
        return -1;
    }

    private static String displayNameFor(String id) {
        if (ITEM_ALPHA.equals(id)) return "Alpha Core";
        if (ITEM_BETA.equals(id))  return "Beta Core";
        if (ITEM_GAMMA.equals(id)) return "Gamma Core";
        return id;
    }
}
