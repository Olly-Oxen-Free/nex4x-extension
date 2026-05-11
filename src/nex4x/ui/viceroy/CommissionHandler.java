package nex4x.ui.viceroy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;

import java.util.List;

public class CommissionHandler {
    public static void open(InteractionDialogAPI dialog, MarketAPI market) {
        FactionCommissionIntel current = findActive();
        if (current == null) {
            dialog.getTextPanel().addPara("No commission currently held. "
                    + "You could apply for one with " + market.getFaction().getDisplayName() + ".");
        } else if (current.getFaction().getId().equals(market.getFactionId())) {
            dialog.getTextPanel().addPara("You hold a commission with this faction. "
                    + "Your position within their service is recognized by " + market.getFaction().getDisplayName() + ".");
        } else {
            dialog.getTextPanel().addPara("You already hold a commission with "
                    + current.getFaction().getDisplayName() + ". "
                    + "You may resign it to apply here.");
        }
        dialog.getTextPanel().addPara("(Commission grant/resign wiring — Phase 12)");
    }

    static FactionCommissionIntel findActive() {
        List<IntelInfoPlugin> all = Global.getSector().getIntelManager()
                .getIntel(FactionCommissionIntel.class);
        for (IntelInfoPlugin i : all) {
            FactionCommissionIntel fci = (FactionCommissionIntel) i;
            if (!fci.isEnded() && !fci.isEnding()) return fci;
        }
        return null;
    }
}
