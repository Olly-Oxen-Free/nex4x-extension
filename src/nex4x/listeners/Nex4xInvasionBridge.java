package nex4x.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import exerelin.campaign.InvasionRound.InvasionRoundResult;
import exerelin.utilities.InvasionListener;
import nex4x.managers.Nex4xManager;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Receives Nex {@link InvasionListener} callbacks so Nex4x can react to market transfers / invasions.
 */
public class Nex4xInvasionBridge implements InvasionListener {

    private static final Logger log = Global.getLogger(Nex4xInvasionBridge.class);

    @Override
    public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market,
                                  Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {
    }

    @Override
    public void reportInvasionRound(InvasionRoundResult result, CampaignFleetAPI fleet,
                                    MarketAPI defender, float atkStr, float defStr) {
    }

    @Override
    public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction,
                                       MarketAPI market, float numRounds, boolean success) {
    }

    @Override
    public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner,
                                       boolean playerInvolved, boolean isCapture,
                                       List<String> factionsToNotify, float repChangeStrength) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null || market == null || newOwner == null || oldOwner == null) return;
        try {
            mgr.getReactiveHandler().onMarketTransferred(mgr, market, newOwner, oldOwner, isCapture);
        } catch (Exception e) {
            log.warn("[Nex4x] onMarketTransferred: " + e.getMessage());
        }
    }
}
