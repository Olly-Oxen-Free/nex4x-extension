package nex4x.leaders;

public enum ReputationTier {
    HOSTILE(-100f, -50f),
    SUSPICIOUS(-50f, -25f),
    NEUTRAL(-25f, 10f),
    FAVORABLE(10f, 50f),
    COOPERATIVE(50f, 100f);

    public final float minRelation;
    public final float maxRelation;

    ReputationTier(float min, float max) {
        this.minRelation = min;
        this.maxRelation = max;
    }

    public static ReputationTier fromRelation(float r) {
        if (r <= -50f) return HOSTILE;
        if (r < -25f)  return SUSPICIOUS;
        if (r < 10f)   return NEUTRAL;
        if (r < 50f)   return FAVORABLE;
        return COOPERATIVE;
    }

    public static ReputationTier shift(ReputationTier base, int delta) {
        int idx = base.ordinal() + delta;
        if (idx < 0) idx = 0;
        if (idx >= values().length) idx = values().length - 1;
        return values()[idx];
    }
}
