package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
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
import nex4x.ui.viceroy.AiCoreHandler;
import nex4x.ui.viceroy.CommissionHandler;
import nex4x.ui.viceroy.IntelPurchaseHandler;
import nex4x.ui.viceroy.WetworkHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple InteractionDialogPlugin: shows viceroy greeting + service menu.
 * Routes option IDs to sub-menu phases for AI-core tier, intel tier, and wetwork target.
 *
 * Phase 0 = main menu
 * Phase 1 = AI-core tier selection
 * Phase 2 = intel tier selection
 * Phase 3 = wetwork target faction selection
 * Phase 4 = commission sub-menu (apply/resign)
 */
@SuppressWarnings("rawtypes")
public class ViceroyDialog implements InteractionDialogPlugin {

    // ── Main-menu options ──────────────────────────────────────────────────
    public static final String OPT_ACCEPT_QUESTS  = "vic_quests";
    public static final String OPT_BUY_INTEL      = "vic_intel";
    public static final String OPT_AI_CORES       = "vic_aicore";
    public static final String OPT_COMMISSION     = "vic_commission";
    public static final String OPT_WETWORK        = "vic_wetwork";
    public static final String OPT_DONE           = "vic_done";

    // ── Sub-menu options ───────────────────────────────────────────────────
    public static final String OPT_BACK            = "vic_back";

    // AI-core tier
    public static final String OPT_TIER_ALPHA      = "vic_tier_alpha";
    public static final String OPT_TIER_BETA       = "vic_tier_beta";
    public static final String OPT_TIER_GAMMA      = "vic_tier_gamma";

    // Intel tier
    public static final String OPT_INTEL_LOCATION  = "vic_intel_location";
    public static final String OPT_INTEL_GOALS     = "vic_intel_goals";
    public static final String OPT_INTEL_BOUNTY    = "vic_intel_bounty";

    // Wetwork target prefix (each faction option is this + factionId)
    private static final String OPT_WETWORK_PREFIX = "vic_wetwork_";

    // ── State ──────────────────────────────────────────────────────────────
    private final MarketAPI market;
    private InteractionDialogAPI dialog;
    private TextPanelAPI text;

    /** 0=main, 1=AI-core tier, 2=intel tier, 3=wetwork target, 4=commission */
    private int phase = 0;

    public ViceroyDialog(MarketAPI market) {
        this.market = market;
    }

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.text = dialog.getTextPanel();
        showMenu();
    }

    // ── Phase renderers ────────────────────────────────────────────────────

    private void showMenu() {
        phase = 0;
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

    private void showAiCoreTierMenu() {
        phase = 1;
        AiCoreHandler.open(dialog, market);
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption(
                "Alpha Core (" + AiCoreHandler.PRICE_ALPHA + " cr)", OPT_TIER_ALPHA);
        dialog.getOptionPanel().addOption(
                "Beta Core  (" + AiCoreHandler.PRICE_BETA  + " cr)", OPT_TIER_BETA);
        dialog.getOptionPanel().addOption(
                "Gamma Core (" + AiCoreHandler.PRICE_GAMMA + " cr)", OPT_TIER_GAMMA);
        dialog.getOptionPanel().addOption("Back", OPT_BACK);
    }

    private void showIntelTierMenu() {
        phase = 2;
        IntelPurchaseHandler.open(dialog, market);
        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption(
                IntelPurchaseHandler.IntelTier.LOCATION_TIP.displayName
                        + " (" + IntelPurchaseHandler.IntelTier.LOCATION_TIP.price + " cr)",
                OPT_INTEL_LOCATION);
        dialog.getOptionPanel().addOption(
                IntelPurchaseHandler.IntelTier.FACTION_GOALS.displayName
                        + " (" + IntelPurchaseHandler.IntelTier.FACTION_GOALS.price + " cr)",
                OPT_INTEL_GOALS);
        dialog.getOptionPanel().addOption(
                IntelPurchaseHandler.IntelTier.BOUNTY_TARGETS.displayName
                        + " (" + IntelPurchaseHandler.IntelTier.BOUNTY_TARGETS.price + " cr)",
                OPT_INTEL_BOUNTY);
        dialog.getOptionPanel().addOption("Back", OPT_BACK);
    }

    private void showWetworkTargetMenu() {
        phase = 3;
        WetworkHandler.open(dialog, market);
        dialog.getOptionPanel().clearOptions();
        List<FactionAPI> targets = WetworkHandler.getEligibleTargets(market);
        for (FactionAPI f : targets) {
            dialog.getOptionPanel().addOption(f.getDisplayName(),
                    OPT_WETWORK_PREFIX + f.getId());
        }
        if (targets.isEmpty()) {
            text.addPara("No eligible targets at this time.");
        }
        dialog.getOptionPanel().addOption("Back", OPT_BACK);
    }

    private void showCommissionMenu() {
        phase = 4;
        dialog.getOptionPanel().clearOptions();
        CommissionHandler.open(dialog, market);
        dialog.getOptionPanel().addOption("Back", OPT_BACK);
    }

    // ── Option routing ─────────────────────────────────────────────────────

    public void optionSelected(String optionText, Object optionData) {
        if (optionData == null) return;
        String id = optionData.toString();

        // Universal back
        if (OPT_BACK.equals(id)) { showMenu(); return; }

        // Main menu
        if (OPT_DONE.equals(id))         { dialog.dismiss(); return; }
        if (OPT_ACCEPT_QUESTS.equals(id)){ nex4x.ui.viceroy.QuestsHandler.open(dialog, market); showMenu(); return; }
        if (OPT_BUY_INTEL.equals(id))    { showIntelTierMenu(); return; }
        if (OPT_AI_CORES.equals(id))     { showAiCoreTierMenu(); return; }
        if (OPT_COMMISSION.equals(id))   { showCommissionMenu(); return; }
        if (OPT_WETWORK.equals(id))      { showWetworkTargetMenu(); return; }

        // Phase 1: AI-core tier
        if (phase == 1) {
            String coreId = null;
            if (OPT_TIER_ALPHA.equals(id)) coreId = AiCoreHandler.ITEM_ALPHA;
            else if (OPT_TIER_BETA.equals(id))  coreId = AiCoreHandler.ITEM_BETA;
            else if (OPT_TIER_GAMMA.equals(id)) coreId = AiCoreHandler.ITEM_GAMMA;
            if (coreId != null) {
                AiCoreHandler.purchase(dialog, market, coreId);
                showMenu();
                return;
            }
        }

        // Phase 2: intel tier
        if (phase == 2) {
            IntelPurchaseHandler.IntelTier tier = null;
            if (OPT_INTEL_LOCATION.equals(id)) tier = IntelPurchaseHandler.IntelTier.LOCATION_TIP;
            else if (OPT_INTEL_GOALS.equals(id))  tier = IntelPurchaseHandler.IntelTier.FACTION_GOALS;
            else if (OPT_INTEL_BOUNTY.equals(id)) tier = IntelPurchaseHandler.IntelTier.BOUNTY_TARGETS;
            if (tier != null) {
                IntelPurchaseHandler.purchase(dialog, market, tier);
                showMenu();
                return;
            }
        }

        // Phase 3: wetwork target
        if (phase == 3 && id.startsWith(OPT_WETWORK_PREFIX)) {
            String targetFactionId = id.substring(OPT_WETWORK_PREFIX.length());
            WetworkHandler.contract(dialog, market, targetFactionId);
            showMenu();
            return;
        }

        // Phase 4: commission sub-options
        if (phase == 4) {
            if (CommissionHandler.OPT_APPLY.equals(id)) {
                CommissionHandler.handleApply(dialog, market);
                showMenu();
                return;
            }
            if (CommissionHandler.OPT_RESIGN.equals(id)) {
                CommissionHandler.handleResign(dialog);
                showMenu();
                return;
            }
            if (CommissionHandler.OPT_RESIGN_REAPPLY.equals(id)) {
                CommissionHandler.handleResignReapply(dialog, market);
                showMenu();
                return;
            }
        }
    }

    public void optionMousedOver(String optionText, Object optionData) {}
    public void advance(float amount) {}
    public void backFromEngagement(EngagementResultAPI r) {}
    public Object getContext() { return null; }
    public Map<String, MemoryAPI> getMemoryMap() { return new HashMap<String, MemoryAPI>(); }
}
