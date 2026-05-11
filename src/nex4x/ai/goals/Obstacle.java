package nex4x.ai.goals;

import java.io.Serializable;

public class Obstacle implements Serializable {
    private static final long serialVersionUID = 1L;

    public final ObstacleType type;
    public final String sourceFactionId;
    public float severity;  // 0-100
    public final String details;

    public Obstacle(ObstacleType type, String sourceFactionId, float severity, String details) {
        this.type = type;
        this.sourceFactionId = sourceFactionId;
        this.severity = Math.max(0, Math.min(100, severity));
        this.details = details;
    }

    public static Obstacle none() {
        return new Obstacle(ObstacleType.NONE, null, 0, "No obstacle");
    }

    /** ACTIONABLE < 10, PREPARING 10-49, BLOCKED >= 50 */
    public boolean isActionable() { return severity < 10; }
    public boolean isPreparing() { return severity >= 10 && severity < 50; }
    public boolean isBlocked() { return severity >= 50; }
}
