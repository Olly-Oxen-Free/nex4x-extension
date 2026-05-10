package nex4x.leaders;

import com.fs.starfarer.api.campaign.FactionAPI;
import nex4x.util.Nex4xRelations;

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

    /** Classify by percent-scale relation (-100..100). */
    public static ReputationTier fromPercent(float pct) {
        if (pct <= -50f) return HOSTILE;
        if (pct < -25f)  return SUSPICIOUS;
        if (pct < 10f)   return NEUTRAL;
        if (pct < 50f)   return FAVORABLE;
        return COOPERATIVE;
    }

    /** Classify by raw FactionAPI relation [-1..1]. Preferred entry point. */
    public static ReputationTier fromRawRelation(float rawRel) {
        return fromPercent(Nex4xRelations.toPercent(rawRel));
    }

    /** Convenience: classify between two factions (null-safe; null returns NEUTRAL). */
    public static ReputationTier between(FactionAPI a, FactionAPI b) {
        if (a == null || b == null) return NEUTRAL;
        return fromRawRelation(a.getRelationship(b.getId()));
    }

    /**
     * @deprecated misnamed: thresholds are percent. Callers passing raw [-1..1] always got HOSTILE.
     * Use {@link #fromPercent(float)} or {@link #fromRawRelation(float)}.
     */
    @Deprecated
    public static ReputationTier fromRelation(float r) {
        return fromPercent(r);
    }

    public static ReputationTier shift(ReputationTier base, int delta) {
        int idx = base.ordinal() + delta;
        if (idx < 0) idx = 0;
        if (idx >= values().length) idx = values().length - 1;
        return values()[idx];
    }
}
