package nex4x.badges;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Per-faction badge state. Tracks active badges and their progress/decay.
 */
public class FactionBadges implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String factionId;

    // Badge progress counters (for badges that need N triggers, like Warmonger needing 3 wars)
    private final EnumMap<BadgeType, Integer> progress = new EnumMap<BadgeType, Integer>(BadgeType.class);

    // Active badges with remaining days (-1 = permanent)
    private final EnumMap<BadgeType, Float> activeBadges = new EnumMap<BadgeType, Float>(BadgeType.class);

    public FactionBadges(String factionId) {
        this.factionId = factionId;
    }

    /** Earn a badge immediately. */
    public void earnBadge(BadgeType badge) {
        activeBadges.put(badge, badge.decayDays);
    }

    /** Increment progress toward a badge. Returns true if the badge was earned. */
    public boolean incrementProgress(BadgeType badge, int threshold) {
        Integer current = progress.get(badge);
        if (current == null) current = 0;
        current++;
        progress.put(badge, current);

        if (current >= threshold && !hasBadge(badge)) {
            earnBadge(badge);
            return true;
        }
        return false;
    }

    /** Advance decay on all active badges. */
    public void advanceDecay(float days) {
        EnumMap<BadgeType, Float> toRemove = new EnumMap<BadgeType, Float>(BadgeType.class);
        for (Map.Entry<BadgeType, Float> entry : activeBadges.entrySet()) {
            float remaining = entry.getValue();
            if (remaining < 0) continue;  // permanent
            remaining -= days;
            if (remaining <= 0) {
                toRemove.put(entry.getKey(), 0f);
            } else {
                activeBadges.put(entry.getKey(), remaining);
            }
        }
        for (BadgeType bt : toRemove.keySet()) {
            activeBadges.remove(bt);
        }
    }

    public boolean hasBadge(BadgeType badge) { return activeBadges.containsKey(badge); }

    /** Progress counter toward earning a badge (0 if none). */
    public int getProgress(BadgeType badge) {
        Integer v = progress.get(badge);
        return v != null ? v : 0;
    }

    public EnumMap<BadgeType, Float> getActiveBadges() { return new EnumMap<BadgeType, Float>(activeBadges); }
    public String getFactionId() { return factionId; }
}
