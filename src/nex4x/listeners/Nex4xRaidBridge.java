package nex4x.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import exerelin.campaign.intel.fleets.RaidListener;
import nex4x.managers.Nex4xManager;
import org.apache.log4j.Logger;

/** Bridges Nex raid completion into Nex4x memory / pressure. */
public class Nex4xRaidBridge implements RaidListener {

    private static final Logger log = Global.getLogger(Nex4xRaidBridge.class);

    @Override
    public void reportRaidEnded(RaidIntel intel, FactionAPI attacker, FactionAPI defender,
                                MarketAPI target, boolean success) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null || target == null) return;
        try {
            mgr.getReactiveHandler().onRaidValuables(mgr, target, attacker);
        } catch (Exception e) {
            log.warn("[Nex4x] reportRaidEnded: " + e.getMessage());
        }
    }
}
