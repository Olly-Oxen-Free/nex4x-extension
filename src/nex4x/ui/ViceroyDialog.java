package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.leaders.LeaderConfig;
import nex4x.leaders.LeaderConfigRegistry;
import nex4x.leaders.LeaderProfile;
import nex4x.managers.Nex4xManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple InteractionDialogPlugin: shows viceroy greeting + service menu.
 * Routes option IDs to lazily-scaffolded handlers (commission/intel/ai-core/wetwork).
 * Full service logic (Task 3.6) layers on top of this skeleton.
 */
@SuppressWarnings("rawtypes")
public class ViceroyDialog implements InteractionDialogPlugin {

    public static final String OPT_ACCEPT_QUESTS  = "vic_quests";
    public static final String OPT_BUY_INTEL      = "vic_intel";
    public static final String OPT_AI_CORES       = "vic_aicore";
    public static final String OPT_COMMISSION     = "vic_commission";
    public static final String OPT_WETWORK        = "vic_wetwork";
    public static final String OPT_DONE           = "vic_done";

    private final MarketAPI market;
    private InteractionDialogAPI dialog;
    private TextPanelAPI text;

    public ViceroyDialog(MarketAPI market) {
        this.market = market;
    }

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.text = dialog.getTextPanel();
        showMenu();
    }

    private void showMenu() {
        String fid = market.getFactionId();
        LeaderConfig cfg = LeaderConfigRegistry.get(fid);
        LeaderProfile profile = Nex4xManager.getOrCreateManager()
                .getLeaderRegistry().getProfile(fid);
        text.addPara("You are received in the antechamber by the "
                + cfg.viceroyTitle + ", speaking on behalf of "
                + profile.displayName() + ".");

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Inquire after faction quests", OPT_ACCEPT_QUESTS);
        dialog.getOptionPanel().addOption("Purchase intelligence", OPT_BUY_INTEL);
        dialog.getOptionPanel().addOption("Discuss AI core transfer", OPT_AI_CORES);
        dialog.getOptionPanel().addOption("Commission matters", OPT_COMMISSION);
        dialog.getOptionPanel().addOption("Contract a wetwork job", OPT_WETWORK);
        dialog.getOptionPanel().addOption("Depart", OPT_DONE);

        if (!cfg.canSellAiCores) {
            dialog.getOptionPanel().setEnabled(OPT_AI_CORES, false);
            dialog.getOptionPanel().setTooltip(OPT_AI_CORES,
                    "The " + cfg.viceroyTitle + " stares at you flatly. 'We do not trade in such things.'");
        }
        if (!cfg.canSellWetwork) {
            dialog.getOptionPanel().setEnabled(OPT_WETWORK, false);
            dialog.getOptionPanel().setTooltip(OPT_WETWORK,
                    cfg.noteOnLuddicDenial
                        ? "'Ludd teaches that peace is built on hands unstained.' The offer is refused before it is made."
                        : "Not available at this post.");
        }
    }

    public void optionSelected(String optionText, Object optionData) {
        if (optionData == null) return;
        String id = optionData.toString();
        if (OPT_ACCEPT_QUESTS.equals(id)) { text.addPara("[Quests stub — Task 3.6]"); showMenu(); return; }
        if (OPT_BUY_INTEL.equals(id))     { text.addPara("[Intel stub — Task 3.6]"); showMenu(); return; }
        if (OPT_AI_CORES.equals(id))      { text.addPara("[AI cores stub — Task 3.6]"); showMenu(); return; }
        if (OPT_COMMISSION.equals(id))    { text.addPara("[Commission stub — Task 3.6]"); showMenu(); return; }
        if (OPT_WETWORK.equals(id))       { text.addPara("[Wetwork stub — Task 3.6]"); showMenu(); return; }
        if (OPT_DONE.equals(id)) {
            dialog.dismiss();
            return;
        }
    }

    public void optionMousedOver(String optionText, Object optionData) {}
    public void advance(float amount) {}
    public void backFromEngagement(EngagementResultAPI r) {}
    public Object getContext() { return null; }
    public Map<String, MemoryAPI> getMemoryMap() { return new HashMap<String, MemoryAPI>(); }
}
