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

    /**
     * Resolve which gate (if any) unlocks audience with target faction's leader.
     * NOTE: rapportThreshold from leader_access.json is in RAW scale (-1..+1), matching
     * FactionAPI.getRelationship(). DeclarationConfig elsewhere uses percent (-100..+100) —
     * see Nex4xRelations for conversion helpers if porting JSON values.
     */
    public static Gate resolve(String targetFactionId) {
        float rapportNeed = LeaderAccessConfig.getRapportThreshold(targetFactionId);
        FactionAPI player = Global.getSector() == null ? null : Global.getSector().getPlayerFaction();
        if (player == null) return Gate.NONE;
        if (player.getRelationship(targetFactionId) >= rapportNeed) return Gate.RAPPORT;

        FactionCommissionIntel commission = findActiveCommission();
        if (commission != null && commission.getFaction() != null) {
            if (!LeaderAccessConfig.isCommissionMustMatchTarget()
                    || targetFactionId.equals(commission.getFaction().getId())) {
                return Gate.COMMISSION;
            }
        }

        int needMarkets = LeaderAccessConfig.getOwnMarketCount();
        int owned = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.getFaction() != null && m.getFaction().isPlayerFaction()) {
                owned++;
                if (owned >= needMarkets) return Gate.OWN_FACTION;
            }
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

    /**
     * Forward-looking hook for unlock conditions that aren't reputation, commission, or
     * own-faction. Intended for future plot tokens, diplomat-network unlocks, story-mission
     * gates, etc. Always false in v5; expand here when those systems land.
     */
    static boolean checkOtherMeans(String targetFactionId) {
        return false;
    }
}
