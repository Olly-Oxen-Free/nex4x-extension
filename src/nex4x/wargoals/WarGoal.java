package nex4x.wargoals;

import java.io.Serializable;

/**
 * An active war goal for one faction against another.
 * Created at war declaration; resolved at peace.
 */
public class WarGoal implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String holderFactionId;
    private final String targetFactionId;
    private final WarGoalType type;
    private final String targetMarketId;  // nullable, for TERRITORIAL_CLAIM
    private final float declaredDay;

    private float scopeProgress;  // 0-1, how close to achieving the war goal
    private boolean achieved;
    private boolean abandoned;

    public WarGoal(String holderFactionId, String targetFactionId,
                   WarGoalType type, String targetMarketId, float declaredDay) {
        this.holderFactionId = holderFactionId;
        this.targetFactionId = targetFactionId;
        this.type = type;
        this.targetMarketId = targetMarketId;
        this.declaredDay = declaredDay;
        this.scopeProgress = 0;
        this.achieved = false;
        this.abandoned = false;
    }

    public String getHolderFactionId() { return holderFactionId; }
    public String getTargetFactionId() { return targetFactionId; }
    public WarGoalType getType() { return type; }
    public String getTargetMarketId() { return targetMarketId; }
    public float getDeclaredDay() { return declaredDay; }
    public float getScopeProgress() { return scopeProgress; }
    public boolean isAchieved() { return achieved; }
    public boolean isAbandoned() { return abandoned; }
    public boolean isActive() { return !achieved && !abandoned; }

    public void setScopeProgress(float progress) {
        this.scopeProgress = Math.max(0, Math.min(1, progress));
        if (this.scopeProgress >= 1.0f) this.achieved = true;
    }

    public void abandon() { this.abandoned = true; }

    public String getKey() {
        return holderFactionId + ":" + targetFactionId + ":" + type.name();
    }
}
