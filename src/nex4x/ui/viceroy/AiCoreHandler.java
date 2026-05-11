package nex4x.ui.viceroy;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.leaders.LeaderConfig;
import nex4x.leaders.LeaderConfigRegistry;

public class AiCoreHandler {
    public static void open(InteractionDialogAPI dialog, MarketAPI market) {
        LeaderConfig cfg = LeaderConfigRegistry.get(market.getFactionId());
        if (!cfg.canSellAiCores) {
            dialog.getTextPanel().addPara("'We do not trade in such things,' says the "
                    + cfg.viceroyTitle + " with finality.");
            return;
        }
        dialog.getTextPanel().addPara("The " + cfg.viceroyTitle
                + " gestures you through to a secure consultation room. "
                + "(AI core trade flow — submenu extension in Phase 12)");
    }
}
