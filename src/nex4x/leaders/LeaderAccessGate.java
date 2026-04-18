package nex4x.leaders;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.FactionCommissionIntel;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

/** OR-logic: any one path unlocks leader audience. */
public class LeaderAccessGate {

    public enum Gate { RAPPORT, COMMISSION, OWN_FACTION, OTHER_MEANS, NONE }

    public static Gate resolve(String targetFactionId) {
        // Path 1: rapport ≥ +50
        FactionAPI player = Global.getSector().getPlayerFaction();
        if (player.getRelationship(targetFactionId) >= 0.50f) return Gate.RAPPORT;

        // Path 2: commission credential — player holds a FactionCommissionIntel
        // Any active commission grants diplomatic access to all factions.
        // (Rank checking deferred — API for fetching player rank from FactionCommissionIntel varies by version)
        FactionCommissionIntel commission = findActiveCommission();
        if (commission != null && commission.getFaction() != null) {
            return Gate.COMMISSION;
        }

        // Path 3: player owns ≥ 1 market of their own faction
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.getFaction() != null && m.getFaction().isPlayerFaction()) return Gate.OWN_FACTION;
        }

        // Path 4: "other means" hook — stubbed; future plot tokens, diplomat unlocks
        if (checkOtherMeans(targetFactionId)) return Gate.OTHER_MEANS;

        return Gate.NONE;
    }

    /** True if the gate unlocks — any non-NONE result. */
    public static boolean isOpen(String targetFactionId) {
        return resolve(targetFactionId) != Gate.NONE;
    }

    static FactionCommissionIntel findActiveCommission() {
        List<IntelInfoPlugin> all = Global.getSector().getIntelManager()
                .getIntel(FactionCommissionIntel.class);
        for (IntelInfoPlugin i : all) {
            FactionCommissionIntel fci = (FactionCommissionIntel) i;
            if (!fci.isEnded() && !fci.isEnding()) return fci;
        }
        return null;
    }

    static boolean checkOtherMeans(String targetFactionId) {
        // Stub — future hook. Always false in v5.
        return false;
    }
}
