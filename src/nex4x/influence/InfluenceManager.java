package nex4x.influence;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Faction-wide influence treasury. Per-cycle (30-day) income/expenses.
 * Daily advance accumulates day counter; every 30 days runs cycle income/spend.
 */
public class InfluenceManager implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int CYCLE_DAYS = 30;

    private static final Logger log = Global.getLogger(InfluenceManager.class);

    private final Map<String, InfluenceLedger> ledgers = new HashMap<String, InfluenceLedger>();
    private int daysAccumulated = 0;

    // Config (transient — reloaded on demand)
    private transient Config config;

    public InfluenceLedger getLedger(String factionId) {
        InfluenceLedger l = ledgers.get(factionId);
        if (l == null) {
            l = new InfluenceLedger();
            ledgers.put(factionId, l);
        }
        return l;
    }

    public float getBalance(String factionId) { return getLedger(factionId).getBalance(); }
    public boolean canAfford(String factionId, float amount) { return getLedger(factionId).canAfford(amount); }

    public boolean spend(String factionId, float amount, InfluenceSource src) {
        return getLedger(factionId).spend(amount, src);
    }

    public void addLump(String factionId, float amount, InfluenceSource src) {
        getLedger(factionId).addLump(amount, src);
    }

    public Map<InfluenceSource, Float> getIncomeBreakdown(String factionId) {
        return getLedger(factionId).getIncomeBreakdown();
    }

    public Map<InfluenceSource, Float> getExpenseBreakdown(String factionId) {
        return getLedger(factionId).getExpenseBreakdown();
    }

    /** Call once per day. Every CYCLE_DAYS triggers cycle income. */
    public void advanceDay() {
        daysAccumulated++;
        if (daysAccumulated >= CYCLE_DAYS) {
            daysAccumulated = 0;
            runCycle();
        }
    }

    public void runCycle() {
        Config cfg = getConfig();
        if (cfg == null) return;
        // Reset ledger breakdowns at cycle start
        for (InfluenceLedger l : ledgers.values()) l.resetBreakdown();
        // Apply cycle income per faction
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (faction.isNeutralFaction()) continue;
            if ("player".equals(faction.getId())) continue;
            String fid = faction.getId();
            float income = calculateCycleIncome(fid, cfg);
            if (income > 0) {
                getLedger(fid).applyCycleIncome(income, InfluenceSource.MARKET_SIZE);
            }
        }
    }

    public float calculateCycleIncome(String factionId, Config cfg) {
        float total = 0f;
        List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
        for (MarketAPI m : markets) {
            if (m == null || m.getFaction() == null) continue;
            if (!factionId.equals(m.getFaction().getId())) continue;
            total += m.getSize() * cfg.marketSizeRate;
            total += Math.max(0, m.getStability().getModifiedValue()) * cfg.stabilityRate;
            // Building-sourced income
            for (Map.Entry<String, Float> b : cfg.buildingIncome.entrySet()) {
                if (m.hasIndustry(b.getKey())) total += b.getValue();
            }
            // AI core bonuses (via admin officer)
            if (m.getAdmin() != null) {
                String coreType = m.getAdmin().getAICoreId();
                if (coreType != null) {
                    Float bonus = cfg.aiCoreBonuses.get(coreType);
                    if (bonus != null) total += bonus;
                }
            }
        }
        return total;
    }

    private Config getConfig() {
        if (config == null) config = loadConfig();
        return config;
    }

    private static Config loadConfig() {
        Config cfg = new Config();
        try {
            JSONObject json = Global.getSettings().loadJSON(Nex4xConstants.PATH_INFLUENCE_SOURCES);
            cfg.marketSizeRate = (float) json.optDouble("marketSizeRate", 0.5);
            cfg.stabilityRate = (float) json.optDouble("stabilityRate", 0.1);
            JSONObject buildings = json.optJSONObject("buildingIncome");
            if (buildings != null) {
                java.util.Iterator<String> it = buildings.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    cfg.buildingIncome.put(k, (float) buildings.optDouble(k, 0));
                }
            }
            JSONObject cores = json.optJSONObject("aiCoreBonuses");
            if (cores != null) {
                java.util.Iterator<String> it = cores.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    cfg.aiCoreBonuses.put(k, (float) cores.optDouble(k, 0));
                }
            }
        } catch (Exception e) {
            log.error("[Nex4x] Failed to load influence_sources.json", e);
        }
        return cfg;
    }

    public static class Config {
        public float marketSizeRate = 0.5f;
        public float stabilityRate = 0.1f;
        public final Map<String, Float> buildingIncome = new HashMap<String, Float>();
        public final Map<String, Float> aiCoreBonuses = new HashMap<String, Float>();
    }

    /**
     * Returns a discount factor [0, 0.75] derived from active Friendship or Denouncement
     * declarations between the player and the given faction.
     *
     * @param otherFactionId  the other faction in the declaration
     * @param isFriendlyAction true for actions benefiting the other faction (Friendship applies),
     *                         false for actions against them (Denounce applies)
     * @return discount fraction to subtract from 1.0 when computing cost
     */
    public float getDeclarationDiscountFactor(String otherFactionId, boolean isFriendlyAction) {
        float discount = 0f;
        nex4x.managers.Nex4xManager mgr = nex4x.managers.Nex4xManager.getManager();
        if (mgr == null) return 0f;
        for (nex4x.declarations.Declaration d : mgr.getDeclarationManager()
                .getDeclarationsBetween(
                        com.fs.starfarer.api.Global.getSector().getPlayerFaction().getId(),
                        otherFactionId)) {
            if (!d.isActive()) continue;
            nex4x.declarations.DeclarationConfig cfg =
                    nex4x.declarations.DeclarationConfig.get(d.getType());
            if (cfg.influenceDiscountPct <= 0) continue;
            boolean applicable = isFriendlyAction
                    ? (d.getType() == nex4x.declarations.DeclarationType.FRIENDSHIP)
                    : (d.getType() == nex4x.declarations.DeclarationType.DENOUNCE);
            if (applicable) discount += cfg.influenceDiscountPct / 100f;
        }
        return Math.min(discount, 0.75f); // cap at 75% discount
    }

    public static InfluenceManager get() {
        Object raw = Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_INFLUENCE_MANAGER);
        if (raw instanceof InfluenceManager) return (InfluenceManager) raw;
        return null;
    }

    public static InfluenceManager getOrCreate() {
        InfluenceManager mgr = get();
        if (mgr == null) {
            mgr = new InfluenceManager();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_INFLUENCE_MANAGER, mgr);
        }
        return mgr;
    }
}
