package nex4x.ui.viceroy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.util.MutableValue;
import nex4x.agents.wetwork.WetworkExecutionScript;
import nex4x.integration.NexDiplomacyBridge;
import nex4x.leaders.LeaderConfig;
import nex4x.leaders.LeaderConfigRegistry;
import nex4x.managers.Nex4xManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles player wetwork contract purchases from a viceroy.
 * Task 21e: target picker, credit deduction, MemoryManager contract entry.
 */
public class WetworkHandler {

    public static final float DISCOVERY_REP_PENALTY = -0.25f;  // -25 rep if caught
    public static final int   WETWORK_FEE            = 25000;

    /**
     * Prints flavor text and description.
     * ViceroyDialog calls this before showing the target sub-menu.
     */
    public static void open(InteractionDialogAPI dialog, MarketAPI market) {
        LeaderConfig cfg = LeaderConfigRegistry.get(market.getFactionId());
        if (!cfg.canSellWetwork) {
            if (cfg.noteOnLuddicDenial) {
                dialog.getTextPanel().addPara("'Ludd teaches that peace is built on hands unstained,' "
                        + "the " + cfg.viceroyTitle + " says, not breaking eye contact. "
                        + "'This faith has no work for you.'");
            } else {
                dialog.getTextPanel().addPara("The " + cfg.viceroyTitle
                        + " informs you no such contracts are available here.");
            }
            return;
        }
        TextPanelAPI text = dialog.getTextPanel();
        text.addPara("The " + cfg.viceroyTitle + " meets you in a side room.");
        text.addPara("Contract fee: " + WETWORK_FEE + " credits. "
                + "If discovered: −25 rep with target faction. "
                + "The " + cfg.viceroyTitle + "'s faction will deny involvement.");
        text.addPara("Select a target faction:");
    }

    /**
     * Returns factions eligible as wetwork targets for the given market.
     * Excludes: player faction, market faction, and factions currently hostile to market faction.
     * Note: allied-faction filter is simplified to hostile-only for v1 (documented).
     */
    public static List<FactionAPI> getEligibleTargets(MarketAPI market) {
        FactionAPI marketFaction = market.getFaction();
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        List<FactionAPI> result = new ArrayList<FactionAPI>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.isNeutralFaction()) continue;
            if (f.getId().equals(playerFaction.getId())) continue;
            if (f.getId().equals(marketFaction.getId())) continue;
            if (marketFaction.isHostileTo(f)) continue;
            result.add(f);
        }
        return result;
    }

    /**
     * Executes the wetwork contract: deducts credits and creates memory entry.
     *
     * @param targetFactionId  Faction to be targeted.
     */
    public static void contract(InteractionDialogAPI dialog, MarketAPI market,
                                 String targetFactionId) {
        LeaderConfig cfg = LeaderConfigRegistry.get(market.getFactionId());
        TextPanelAPI text = dialog != null ? dialog.getTextPanel() : null;

        if (!cfg.canSellWetwork) {
            if (text != null) text.addPara("This faction does not offer wetwork services.");
            return;
        }

        MutableValue credits = Global.getSector().getPlayerFleet().getCargo().getCredits();
        if (credits.get() < WETWORK_FEE) {
            if (text != null) {
                text.addPara("Insufficient credits. The contract fee is "
                        + WETWORK_FEE + " credits.");
            }
            return;
        }

        credits.subtract(WETWORK_FEE);
        AddRemoveCommodity.addCreditsLossText(WETWORK_FEE, text);

        // Record the contract in MemoryManager using the wetwork_contract memory type.
        try {
            Nex4xManager.getOrCreateManager().getMemoryManager()
                    .createMemory("wetwork_contract",
                            market.getFactionId(),
                            targetFactionId,
                            "30d wetwork contract");
        } catch (Throwable t) {
            // Memory system unavailable; contract is still paid for but untracked.
        }

        // Schedule the actual outcome: a self-terminating persistent script that resolves
        // success/discovery 7-14 days out and reports back via intel.
        try {
            String contractId = "wet_" + market.getFactionId() + "_" + targetFactionId
                    + "_" + Long.toString((long) (Math.random() * 1000000000L));
            WetworkExecutionScript script = new WetworkExecutionScript(
                    market.getFactionId(), targetFactionId, contractId);
            Global.getSector().addScript(script);
        } catch (Throwable t) {
            Global.getLogger(WetworkHandler.class)
                    .warn("[Nex4x] Failed to schedule wetwork execution: " + t.getMessage(), t);
        }

        if (text != null) {
            text.addPara("Contract accepted. The "
                    + cfg.viceroyTitle + " will deny all involvement. "
                    + "You will hear word within a couple of weeks.");
        }
    }

    /**
     * Discovery consequences: applied when a contract is exposed (the operative is caught).
     * Fires a Nexerelin-consistent insult event player -> target (with an adjustRelationship
     * fallback via {@link NexDiplomacyBridge}), and records the target faction's memory of the
     * player's espionage. Called from {@link WetworkExecutionScript} on a failed contract.
     */
    public static void onDiscovered(String targetFactionId) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        FactionAPI target = Global.getSector().getFaction(targetFactionId);
        if (player == null || target == null) return;

        // Nex-consistent rep hit (DISCOVERY_REP_PENALTY is -0.25 raw = -25 percent points).
        NexDiplomacyBridge.fireInsultEvent(player, target, Math.abs(DISCOVERY_REP_PENALTY) * 100f);

        // The target now remembers the player's espionage.
        try {
            Nex4xManager.getOrCreateManager().getMemoryManager()
                    .createMemory("espionage_discovered",
                            player.getId(),
                            targetFactionId,
                            "Wetwork contract exposed");
        } catch (Throwable t) {
            // Memory system unavailable; rep hit still applied.
        }
    }
}
