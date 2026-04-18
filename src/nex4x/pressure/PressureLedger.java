package nex4x.pressure;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

/** Directional pressure ledger: from faction A -> toward faction B. */
public class PressureLedger implements Serializable {
    private static final long serialVersionUID = 1L;

    private final EnumMap<PressureSource, Float> amounts =
            new EnumMap<PressureSource, Float>(PressureSource.class);

    public float getTotal() {
        float t = 0f;
        for (Float v : amounts.values()) t += v;
        return t;
    }

    public float getAmount(PressureSource src) {
        Float v = amounts.get(src);
        return v == null ? 0f : v;
    }

    public void add(PressureSource src, float amount) {
        float cur = getAmount(src);
        amounts.put(src, Math.max(0f, cur + amount));
    }

    public void spend(float amount) {
        // Spend proportionally across sources (highest first).
        float total = getTotal();
        if (total <= 0 || amount <= 0) return;
        float factor = Math.min(1f, amount / total);
        for (PressureSource s : PressureSource.values()) {
            float v = getAmount(s);
            if (v > 0) amounts.put(s, v * (1f - factor));
        }
    }

    public void applyDecay(Map<PressureSource, Float> perSourceDecay) {
        for (PressureSource s : PressureSource.values()) {
            float v = getAmount(s);
            if (v <= 0) continue;
            Float d = perSourceDecay.get(s);
            if (d != null && d > 0) {
                amounts.put(s, Math.max(0f, v - d));
            }
        }
    }

    public Map<PressureSource, Float> getBreakdown() { return amounts; }
}
