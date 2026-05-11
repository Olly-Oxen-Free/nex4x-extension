package nex4x.ai;

import nex4x.ai.goals.StrategicGoal;
import java.io.Serializable;

/**
 * The AI's "attention" — what it's thinking about right now.
 * Dual-track: strategic objective (long-term) + operational priority (immediate).
 * See AI spec §3.
 */
public class StrategicFocus implements Serializable {
    private static final long serialVersionUID = 1L;

    private StrategicGoal strategicObjective;   // highest importance
    private StrategicGoal operationalPriority;  // highest effective priority
    private Threat greatestThreat;
    private float focusStability;               // days strategic objective has been stable
    private String lastObjectiveKey;

    public void update(StrategicGoal newObjective, StrategicGoal newPriority,
                       Threat newThreat, float daysPassed) {
        String objectiveKey = newObjective != null ? newObjective.getKey() : null;
        if (objectiveKey != null && objectiveKey.equals(lastObjectiveKey)) {
            focusStability += daysPassed;
        } else {
            focusStability = 0;
            lastObjectiveKey = objectiveKey;
        }

        this.strategicObjective = newObjective;
        this.operationalPriority = newPriority;
        this.greatestThreat = newThreat;
    }

    public StrategicGoal getStrategicObjective() { return strategicObjective; }
    public StrategicGoal getOperationalPriority() { return operationalPriority; }
    public Threat getGreatestThreat() { return greatestThreat; }
    public float getFocusStability() { return focusStability; }

    public boolean isConverged() {
        return strategicObjective != null && operationalPriority != null
                && strategicObjective.getKey().equals(operationalPriority.getKey());
    }

    /**
     * Threat data.
     */
    public static class Threat implements Serializable {
        private static final long serialVersionUID = 1L;

        public enum ThreatType {
            MILITARY_AGGRESSION, ECONOMIC_COMPETITION, TERRITORIAL_ENCROACHMENT,
            PRESSURE_DOMINANCE, COALITION_FORMATION, IDEOLOGICAL_SPREAD,
            INTERNAL_INSTABILITY
        }

        public final ThreatType type;
        public final String sourceFactionId;
        public final float severity;
        public final boolean existential;

        public Threat(ThreatType type, String sourceFactionId, float severity, boolean existential) {
            this.type = type;
            this.sourceFactionId = sourceFactionId;
            this.severity = severity;
            this.existential = existential;
        }
    }
}
