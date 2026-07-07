package nex4x.ui.viceroy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import exerelin.campaign.intel.Nex_FactionCommissionIntel;

import java.util.List;

/**
 * Handles player commission grant / resign via a viceroy.
 * Task 21d: real commission grant via Nex_FactionCommissionIntel, resign via endMission.
 */
public class CommissionHandler {

    public static final String OPT_APPLY    = "vic_comm_apply";
    public static final String OPT_RESIGN   = "vic_comm_resign";
    public static final String OPT_RESIGN_REAPPLY = "vic_comm_resign_reapply";

    /**
     * Prints current commission status and adds dialog options.
     * ViceroyDialog must handle the option IDs returned here.
     */
    public static void open(InteractionDialogAPI dialog, MarketAPI market) {
        TextPanelAPI text = dialog.getTextPanel();
        FactionCommissionIntel current = findActive();
        String factionName = market.getFaction().getDisplayName();

        if (current == null) {
            text.addPara("No commission currently held. "
                    + "You could apply for one with " + factionName + ".");
            dialog.getOptionPanel().addOption(
                    "Apply for commission with " + factionName, OPT_APPLY);
        } else if (current.getFaction().getId().equals(market.getFactionId())) {
            text.addPara("You hold a commission with " + factionName
                    + ". Your standing is recognized.");
            dialog.getOptionPanel().addOption("Resign commission", OPT_RESIGN);
        } else {
            text.addPara("You already hold a commission with "
                    + current.getFaction().getDisplayName()
                    + ". You may resign it to apply here with " + factionName + ".");
            dialog.getOptionPanel().addOption(
                    "Resign " + current.getFaction().getDisplayName()
                            + " and apply with " + factionName, OPT_RESIGN_REAPPLY);
        }
    }

    /**
     * Called when OPT_APPLY is selected from ViceroyDialog.
     */
    public static void handleApply(InteractionDialogAPI dialog, MarketAPI market) {
        grantCommission(dialog, market);
    }

    /**
     * Called when OPT_RESIGN is selected.
     */
    public static void handleResign(InteractionDialogAPI dialog) {
        TextPanelAPI text = dialog.getTextPanel();
        FactionCommissionIntel current = findActive();
        if (current == null) {
            text.addPara("You don't hold an active commission.");
            return;
        }
        current.endMission(dialog);
        text.addPara("Commission with " + current.getFaction().getDisplayName() + " resigned.");
    }

    /**
     * Called when OPT_RESIGN_REAPPLY is selected.
     */
    public static void handleResignReapply(InteractionDialogAPI dialog, MarketAPI market) {
        FactionCommissionIntel current = findActive();
        if (current != null) {
            current.endMission(dialog);
        }
        grantCommission(dialog, market);
    }

    /**
     * Creates and registers a Nex_FactionCommissionIntel for the market's faction.
     * Guards against double-grant.
     */
    static void grantCommission(InteractionDialogAPI dialog, MarketAPI market) {
        TextPanelAPI text = dialog != null ? dialog.getTextPanel() : null;

        // Double-grant guard
        if (findActive() != null) {
            if (text != null) text.addPara("You already hold an active commission.");
            return;
        }

        FactionAPI faction = market.getFaction();
        try {
            Nex_FactionCommissionIntel intel = new Nex_FactionCommissionIntel(faction);
            Global.getSector().getIntelManager().addIntel(intel, false, text);
            intel.missionAccepted();
            if (text != null) {
                text.addPara("You now hold a commission with "
                        + faction.getDisplayName() + ".");
            }
        } catch (Throwable t) {
            if (text != null) {
                text.addPara("Commission could not be issued at this time.");
            }
        }
    }

    /** Finds the first active (non-ended, non-ending) commission intel. */
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
