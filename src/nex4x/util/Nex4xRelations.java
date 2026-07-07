package nex4x.util;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;

/**
 * Centralized helpers for relationship-scale conversion.
 *
 * Starsector's FactionAPI.getRelationship() returns a float in [-1.0, +1.0].
 * Most nex4x configuration (DeclarationConfig JSON, ReputationTier thresholds)
 * is expressed in [-100, +100]. This class is the single conversion point.
 */
public final class Nex4xRelations {

    private Nex4xRelations() {}

    /** Convert raw API relation [-1..1] to percent [-100..100]. */
    public static float toPercent(float rawRel) {
        return rawRel * 100f;
    }

    /** Convert raw API relation to a rounded percent int (for display). */
    public static int toPercentInt(float rawRel) {
        return Math.round(rawRel * 100f);
    }

    /** True if rawRel * 100 >= pct. Use to compare raw API rel against percent thresholds. */
    public static boolean atLeastPct(float rawRel, float pct) {
        return rawRel * 100f >= pct;
    }

    /** True if rawRel * 100 <= pct. */
    public static boolean atMostPct(float rawRel, float pct) {
        return rawRel * 100f <= pct;
    }

    /** Wrapper for RepLevel.getLevelFor — returns the engine's bucket for a raw rel. */
    public static RepLevel repLevel(float rawRel) {
        return RepLevel.getLevelFor(rawRel);
    }

    /** Convenience: percent-int between two factions, null-safe. Returns 0 on null. */
    public static int percentBetween(FactionAPI a, FactionAPI b) {
        if (a == null || b == null) return 0;
        return toPercentInt(a.getRelationship(b.getId()));
    }
}
