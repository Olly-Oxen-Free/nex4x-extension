package nex4x.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class FactionMarketUtil {
    public static MarketAPI firstMarketOfFaction(String factionId) {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId())) return m;
        }
        return null;
    }
}
