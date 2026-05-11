package nex4x.ui.viceroy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.leaders.LeaderConfig;
import nex4x.leaders.LeaderConfigRegistry;

public class WetworkHandler {
    public static final float DISCOVERY_REP_PENALTY = -0.25f;  // -25 rep if caught

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
        dialog.getTextPanel().addPara("The " + cfg.viceroyTitle + " meets you in a side room.");
        dialog.getTextPanel().addPara("Available targets: factions not currently at war with "
                + market.getFaction().getDisplayName() + " and not aligned with them.");
        dialog.getTextPanel().addPara("On completion: bounty paid quietly, no rep credit. "
                + "If discovered: −25 rep with target faction, possible revenge. "
                + "The " + cfg.viceroyTitle + "'s faction will deny involvement.");
        dialog.getTextPanel().addPara("(Target selection + contract intel — Phase 12)");
    }

    /** Called from wetwork-contract completion code once that ships. */
    public static void onDiscovered(String targetFactionId) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        player.adjustRelationship(targetFactionId, DISCOVERY_REP_PENALTY);
    }
}
