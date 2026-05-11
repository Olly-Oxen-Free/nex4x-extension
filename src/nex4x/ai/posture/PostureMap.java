package nex4x.ai.posture;

import nex4x.ai.goals.StrategicGoal;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-target-faction posture derived from highest-priority goal involving them.
 * Constrains StrategicAI military concerns.
 */
public class PostureMap implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, DiplomaticPosture> postures =
            new HashMap<String, DiplomaticPosture>();

    /** Rebuild from active goals. Called daily after goal scoring. */
    public void rebuild(List<StrategicGoal> activeGoals) {
        postures.clear();

        for (StrategicGoal goal : activeGoals) {
            if (goal.targetFactionId == null) continue;

            DiplomaticPosture existing = postures.get(goal.targetFactionId);
            DiplomaticPosture goalPosture = goal.getPosture();

            if (existing == null) {
                postures.put(goal.targetFactionId, goalPosture);
            }
        }
    }

    public DiplomaticPosture getPosture(String targetFactionId) {
        DiplomaticPosture p = postures.get(targetFactionId);
        return p != null ? p : DiplomaticPosture.NEGOTIATING;
    }

    public Map<String, DiplomaticPosture> getAll() {
        return new HashMap<String, DiplomaticPosture>(postures);
    }
}
