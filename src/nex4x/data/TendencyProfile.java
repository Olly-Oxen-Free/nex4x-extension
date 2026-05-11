package nex4x.data;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

public class TendencyProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private final EnumMap<TendencyId, Float> weights;

    /** No-arg constructor — initializes all tendencies to 0. */
    public TendencyProfile() {
        this.weights = new EnumMap<TendencyId, Float>(TendencyId.class);
        for (TendencyId t : TendencyId.values()) {
            this.weights.put(t, 0f);
        }
    }

    public TendencyProfile(Map<TendencyId, Float> weights) {
        this.weights = new EnumMap<TendencyId, Float>(TendencyId.class);
        for (TendencyId t : TendencyId.values()) {
            this.weights.put(t, weights.containsKey(t) ? weights.get(t) : 0f);
        }
    }

    public float getWeight(TendencyId tendency) {
        Float w = weights.get(tendency);
        return w != null ? w : 0f;
    }

    /** Alias for getWeight — used by AI engine code. */
    public float get(TendencyId tendency) {
        return getWeight(tendency);
    }

    /** Set a tendency value — used by auto-derive and dynamic modifiers. */
    public void set(TendencyId tendency, float value) {
        weights.put(tendency, value);
    }

    public float getTotal() {
        float sum = 0;
        for (float v : weights.values()) sum += v;
        return sum;
    }

    public TendencyId getDominant() {
        TendencyId best = null;
        float bestVal = -Float.MAX_VALUE;
        for (Map.Entry<TendencyId, Float> e : weights.entrySet()) {
            if (e.getValue() > bestVal) {
                bestVal = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    public EnumMap<TendencyId, Float> getWeights() {
        return new EnumMap<TendencyId, Float>(weights);
    }
}
