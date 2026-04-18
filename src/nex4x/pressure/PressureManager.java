package nex4x.pressure;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Bilateral asymmetric pressure tracker. pressure(A -> B) is independent of pressure(B -> A).
 * Daily advance applies decay from pressure_sources.json.
 */
public class PressureManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(PressureManager.class);

    // from -> (to -> ledger)
    private final Map<String, Map<String, PressureLedger>> ledgers =
            new HashMap<String, Map<String, PressureLedger>>();

    private transient Config config;

    public PressureLedger getLedger(String fromFaction, String toFaction) {
        Map<String, PressureLedger> inner = ledgers.get(fromFaction);
        if (inner == null) {
            inner = new HashMap<String, PressureLedger>();
            ledgers.put(fromFaction, inner);
        }
        PressureLedger l = inner.get(toFaction);
        if (l == null) {
            l = new PressureLedger();
            inner.put(toFaction, l);
        }
        return l;
    }

    public float getPressure(String fromFaction, String toFaction) {
        return getLedger(fromFaction, toFaction).getTotal();
    }

    /** Net pressure (A vs B): positive = A has leverage over B. */
    public float getNet(String factionA, String factionB) {
        return getPressure(factionA, factionB) - getPressure(factionB, factionA);
    }

    public Map<PressureSource, Float> getSources(String fromFaction, String toFaction) {
        return getLedger(fromFaction, toFaction).getBreakdown();
    }

    public void applyEvent(String fromFaction, String toFaction, PressureSource source, float amount) {
        getLedger(fromFaction, toFaction).add(source, amount);
    }

    public void spend(String fromFaction, String toFaction, float amount) {
        getLedger(fromFaction, toFaction).spend(amount);
    }

    /** Daily tick: apply per-source decay globally. */
    public void advanceDay() {
        Config cfg = getConfig();
        if (cfg == null) return;
        for (Map<String, PressureLedger> inner : ledgers.values()) {
            for (PressureLedger l : inner.values()) {
                l.applyDecay(cfg.decayPerDay);
            }
        }
    }

    private Config getConfig() {
        if (config == null) config = loadConfig();
        return config;
    }

    private static Config loadConfig() {
        Config cfg = new Config();
        try {
            JSONObject json = Global.getSettings().loadJSON(Nex4xConstants.PATH_PRESSURE_SOURCES);
            JSONObject decay = json.optJSONObject("decayPerDay");
            if (decay != null) {
                java.util.Iterator<String> it = decay.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    try {
                        PressureSource src = PressureSource.valueOf(k);
                        cfg.decayPerDay.put(src, (float) decay.optDouble(k, 0));
                    } catch (IllegalArgumentException ignore) { }
                }
            }
            JSONObject rates = json.optJSONObject("accumulationPerDay");
            if (rates != null) {
                java.util.Iterator<String> it = rates.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    try {
                        PressureSource src = PressureSource.valueOf(k);
                        cfg.accumulationPerDay.put(src, (float) rates.optDouble(k, 0));
                    } catch (IllegalArgumentException ignore) { }
                }
            }
        } catch (Exception e) {
            log.error("[Nex4x] Failed to load pressure_sources.json", e);
        }
        return cfg;
    }

    public static class Config {
        public final EnumMap<PressureSource, Float> decayPerDay =
                new EnumMap<PressureSource, Float>(PressureSource.class);
        public final EnumMap<PressureSource, Float> accumulationPerDay =
                new EnumMap<PressureSource, Float>(PressureSource.class);
    }

    public static PressureManager get() {
        Object raw = Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_PRESSURE_MANAGER);
        if (raw instanceof PressureManager) return (PressureManager) raw;
        return null;
    }

    public static PressureManager getOrCreate() {
        PressureManager mgr = get();
        if (mgr == null) {
            mgr = new PressureManager();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_PRESSURE_MANAGER, mgr);
        }
        return mgr;
    }
}
