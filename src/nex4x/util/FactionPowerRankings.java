package nex4x.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.econ.FleetPoolManager;

import java.util.*;

/**
 * Composite power rank (economic + military + expansion) for browsable factions.
 * Rebuilt each time {@link #rebuild()} is called (e.g. at start of browser render).
 */
public final class FactionPowerRankings {

    private static final Map<String, Float> economic = new HashMap<String, Float>();
    private static final Map<String, Float> military = new HashMap<String, Float>();
    private static final Map<String, Float> expansion = new HashMap<String, Float>();
    private static final Map<String, Integer> rank = new HashMap<String, Integer>();
    private static int denom = 1;

    private FactionPowerRankings() {}

    public static void rebuild() {
        economic.clear();
        military.clear();
        expansion.clear();
        rank.clear();

        List<String> ids = new ArrayList<String>();
        String playerId = Global.getSector().getPlayerFaction().getId();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.isNeutralFaction()) continue;
            if (f.getId().equals("derelict") || f.getId().equals("nex_derelict")) continue;
            if (!hasMarkets(f.getId())) continue;
            ids.add(f.getId());
        }

        denom = Math.max(1, ids.size());

        float maxE = 0f, maxM = 0f, maxX = 0f;
        for (String fid : ids) {
            float e = computeEconomic(fid);
            float m = computeMilitary(fid);
            float x = computeExpansion(fid);
            economic.put(fid, e);
            military.put(fid, m);
            expansion.put(fid, x);
            maxE = Math.max(maxE, e);
            maxM = Math.max(maxM, m);
            maxX = Math.max(maxX, x);
        }
        if (maxE <= 0f) maxE = 1f;
        if (maxM <= 0f) maxM = 1f;
        if (maxX <= 0f) maxX = 1f;

        final float normE = maxE;
        final float normM = maxM;
        final float normX = maxX;

        List<String> sorted = new ArrayList<String>(ids);
        Collections.sort(sorted, new Comparator<String>() {
            public int compare(String a, String b) {
                float ca = composite(a, normE, normM, normX);
                float cb = composite(b, normE, normM, normX);
                return Float.compare(cb, ca);
            }
        });

        int r = 1;
        for (String fid : sorted) {
            rank.put(fid, r++);
        }
    }

    private static boolean hasMarkets(String factionId) {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId())) return true;
        }
        return false;
    }

    private static float computeEconomic(String factionId) {
        float sum = 0f;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!factionId.equals(m.getFactionId())) continue;
            float sz = m.getSize();
            float mult = 1f;
            try {
                if (m.getNetIncome() <= 0f) mult = 0.5f;
            } catch (Exception ignore) {
                mult = 0.5f;
            }
            sum += sz * mult;
        }
        return sum;
    }

    private static float computeMilitary(String factionId) {
        try {
            FleetPoolManager fpm = FleetPoolManager.getManager();
            if (fpm != null) {
                float max = fpm.getMaxPool(factionId);
                if (max > 0f) return max;
            }
        } catch (Exception ignore) {}
        float fallback = 0f;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId())) fallback += m.getSize();
        }
        return fallback;
    }

    private static float computeExpansion(String factionId) {
        int markets = 0;
        Set<String> systems = new HashSet<String>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!factionId.equals(m.getFactionId())) continue;
            markets++;
            try {
                if (m.getStarSystem() != null) {
                    systems.add(m.getStarSystem().getId());
                }
            } catch (Exception ignore) {}
        }
        return markets + systems.size();
    }

    private static float composite(String fid, float maxE, float maxM, float maxX) {
        float e = economic.containsKey(fid) ? economic.get(fid) : 0f;
        float m = military.containsKey(fid) ? military.get(fid) : 0f;
        float x = expansion.containsKey(fid) ? expansion.get(fid) : 0f;
        return (e / maxE + m / maxM + x / maxX) / 3f;
    }

    public static float economicScore(String factionId) {
        Float v = economic.get(factionId);
        return v != null ? v : 0f;
    }

    public static float militaryScore(String factionId) {
        Float v = military.get(factionId);
        return v != null ? v : 0f;
    }

    public static float expansionScore(String factionId) {
        Float v = expansion.get(factionId);
        return v != null ? v : 0f;
    }

    public static String getRankLabel(String factionId) {
        Integer r = rank.get(factionId);
        if (r == null) return "[unranked]";
        return "Rank " + r + "/" + denom;
    }
}
