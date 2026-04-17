package nex4x.ai.goals;

import nex4x.ai.posture.DiplomaticPosture;
import java.io.Serializable;

public class StrategicGoal implements Serializable {
    private static final long serialVersionUID = 1L;

    public final GoalType type;
    public final String targetFactionId;
    public final String targetMarketId;  // nullable, for territorial goals

    private float importance;            // 0-100, slow axis
    private float urgency;               // 0-100, fast axis
    private float effectivePriority;     // derived
    private float progress;              // 0-1
    private DiplomaticPosture posture;
    private Obstacle obstacle;
    private final float createdDay;
    private String parentGoalId;         // nullable

    public StrategicGoal(GoalType type, String targetFactionId, String targetMarketId,
                         float createdDay) {
        this.type = type;
        this.targetFactionId = targetFactionId;
        this.targetMarketId = targetMarketId;
        this.createdDay = createdDay;
        this.importance = 0;
        this.urgency = 0;
        this.effectivePriority = 0;
        this.progress = 0;
        this.posture = DiplomaticPosture.NEGOTIATING;
        this.obstacle = Obstacle.none();
    }

    // Scoring
    public void setImportance(float v) { this.importance = Math.max(0, Math.min(100, v)); }
    public void setUrgency(float v) { this.urgency = Math.max(0, Math.min(100, v)); }
    public void setEffectivePriority(float v) { this.effectivePriority = v; }
    public void setProgress(float v) { this.progress = Math.max(0, Math.min(1, v)); }
    public void setPosture(DiplomaticPosture p) { this.posture = p; }
    public void setObstacle(Obstacle o) { this.obstacle = o; }
    public void setParentGoalId(String id) { this.parentGoalId = id; }

    public float getImportance() { return importance; }
    public float getUrgency() { return urgency; }
    public float getEffectivePriority() { return effectivePriority; }
    public float getProgress() { return progress; }
    public DiplomaticPosture getPosture() { return posture; }
    public Obstacle getObstacle() { return obstacle; }
    public float getCreatedDay() { return createdDay; }
    public String getParentGoalId() { return parentGoalId; }

    /** Unique key for deduplication. */
    public String getKey() {
        String key = type.name() + ":" + targetFactionId;
        if (targetMarketId != null) key += ":" + targetMarketId;
        return key;
    }

    @Override
    public String toString() {
        return type.displayName + " -> " + targetFactionId
                + " (imp=" + Math.round(importance) + " urg=" + Math.round(urgency)
                + " eff=" + Math.round(effectivePriority) + ")";
    }
}
