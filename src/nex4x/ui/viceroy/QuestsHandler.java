package nex4x.ui.viceroy;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class QuestsHandler {
    public static void open(InteractionDialogAPI dialog, MarketAPI market) {
        dialog.getTextPanel().addPara("The " + market.getFactionId()
                + " viceroy waves you toward the posted jobs board.");
        // v5: delegate to vanilla — display the market's mission list in a text summary
        // (Full mission-board UI pipe-through is deferred; this path prints counts.)
        int count = market.getCommDirectory() == null ? 0
                : market.getCommDirectory().getEntriesCopy().size();
        dialog.getTextPanel().addPara("Active postings on the directory: " + count + ".");
    }
}
