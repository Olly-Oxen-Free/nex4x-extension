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

    /**
     * Absolute calendar-day index (cycle*360 + (month-1)*30 + day). For display / serialization.
     * Epoch: cycle 0, day 1. Starsector's calendar is 12 months × 30 days = 360 days/cycle.
     */
    public static float currentAbsoluteDay() {
        CampaignClockAPI c = Global.getSector() == null ? null : Global.getSector().getClock();
        if (c == null) return 0f;
        return c.getCycle() * 360f + (c.getMonth() - 1) * 30f + c.getDay();
    }
}
