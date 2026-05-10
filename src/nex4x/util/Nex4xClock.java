package nex4x.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;

/**
 * Centralized helpers for game-time math.
 *
 * Starsector's CampaignClockAPI.getTimestamp() returns SECONDS (long, opaque epoch).
 * Use this class to convert; never compare timestamps as if they were days.
 *
 * Two distinct day idioms exist in the engine:
 *   - "elapsed days since timestamp" — use daysSince(long)
 *   - "absolute calendar day index"  — use currentAbsoluteDay()
 */
public final class Nex4xClock {

    private Nex4xClock() {}

    /** Current opaque timestamp in seconds. Treat as a baseline for daysSince. */
    public static long now() {
        CampaignClockAPI c = Global.getSector() == null ? null : Global.getSector().getClock();
        return c == null ? 0L : c.getTimestamp();
    }

    /** Days elapsed since {@code ts}. Safe pre-sector: returns 0. */
    public static float daysSince(long ts) {
        CampaignClockAPI c = Global.getSector() == null ? null : Global.getSector().getClock();
        return c == null ? 0f : c.getElapsedDaysSince(ts);
    }

    /** Absolute calendar-day index (cycle*365 + (month-1)*30 + day). For display / serialization. */
    public static float currentAbsoluteDay() {
        CampaignClockAPI c = Global.getSector() == null ? null : Global.getSector().getClock();
        if (c == null) return 0f;
        return c.getCycle() * 365f + (c.getMonth() - 1) * 30f + c.getDay();
    }

    /**
     * Heuristic: pre-fix, several fields stored raw timestamp (seconds since epoch ~ 5e6+)
     * as a "day count". Real day counts are tiny floats. Anything with magnitude > 1e6 is
     * almost certainly a legacy raw-timestamp value loaded from an older save.
     */
    public static boolean isLegacyDayValue(double v) {
        return Math.abs(v) > 1e6;
    }
}
