package nex4x.ai.goals;

import nex4x.ai.archetype.Archetype;
import nex4x.ai.archetype.CommitmentLedger;
import nex4x.data.BeliefDef;
import nex4x.data.BeliefRegistry;
import nex4x.data.FactionBeliefs;
import nex4x.data.FactionBeliefsLoader;
import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;
import org.json.JSONObject;

/**
 * Scores goals on dual axes: importance (slow, identity) and urgency (fast, game-state).
 * See AI spec §2.3.
 */
public class GoalScorer {

    // Context weights (from goal_weights.json)
    private static float IMP_PEACETIME = 0.7f, URG_PEACETIME = 0.3f;
    private static float IMP_URGENT = 0.4f, URG_URGENT = 0.6f;
    private static float IMP_CRISIS = 0.2f, URG_CRISIS = 0.8f;
    private static float CONVERGENCE_BONUS = 20f;
    private static float CONVERGENCE_THRESHOLD = 70f;
    private static float URGENT_GOAL_THRESHOLD = 70f;

    public static void loadConfig(JSONObject config) {
        JSONObject imp = config.optJSONObject("importanceWeights");
        if (imp != null) {
            IMP_PEACETIME = (float) imp.optDouble("peacetime", 0.7);
            IMP_URGENT = (float) imp.optDouble("urgentGoalExists", 0.4);
            IMP_CRISIS = (float) imp.optDouble("crisis", 0.2);
        }
        JSONObject urg = config.optJSONObject("urgencyWeights");
        if (urg != null) {
            URG_PEACETIME = (float) urg.optDouble("peacetime", 0.3);
            URG_URGENT = (float) urg.optDouble("urgentGoalExists", 0.6);
            URG_CRISIS = (float) urg.optDouble("crisis", 0.8);
        }
        CONVERGENCE_BONUS = (float) config.optDouble("convergenceBonus", 20);
        CONVERGENCE_THRESHOLD = (float) config.optDouble("convergenceThreshold", 70);
        URGENT_GOAL_THRESHOLD = (float) config.optDouble("urgentGoalThreshold", 70);
    }

    /**
     * Score importance (slow axis). Recalculated weekly or on archetype change.
     */
    public static float scoreImportance(StrategicGoal goal, String factionId,
                                         CommitmentLedger ledger) {
        TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
        FactionBeliefs beliefs = FactionBeliefsLoader.getBeliefs(factionId);
        Archetype archetype = ledger.getCurrentArchetype();

        float score = 0;

        // Belief relevance (max 25)
        if (beliefs != null) {
            score += beliefRelevance(goal, beliefs) * 25f;
        }

        // Archetype alignment (max 20)
        score += archetypeAlignment(goal, archetype) * 20f;

        // Tendency weight (max 15)
        if (profile != null) {
            score += tendencyWeight(goal, profile) * 15f;
        }

        // Historical weight — how long has this goal existed (max 10)
        float ageDays = getCurrentDay() - goal.getCreatedDay();
        score += Math.min(1f, ageDays / 90f) * 10f;

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Score urgency (fast axis). Recalculated daily.
     */
    public static float scoreUrgency(StrategicGoal goal, String factionId) {
        float score = 0;

        // Time constraint (max 30)
        score += timeConstraint(goal) * 30f;

        // Threat proximity (max 25)
        score += threatProximity(goal, factionId) * 25f;

        // Opportunity window (max 20)
        score += opportunityWindow(goal, factionId) * 20f;

        // Obstacle escalation (max 15)
        score += obstacleEscalation(goal) * 15f;

        // Cascade risk (max 10)
        score += cascadeRisk(goal) * 10f;

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Compute effective priority from importance + urgency with context weighting.
     */
    public static float computeEffectivePriority(float importance, float urgency,
                                                  boolean hasCrisis, boolean hasUrgentGoal,
                                                  Archetype archetype, GoalType goalType) {
        float impWeight, urgWeight;
        if (hasCrisis) {
            impWeight = IMP_CRISIS;
            urgWeight = URG_CRISIS;
        } else if (hasUrgentGoal) {
            impWeight = IMP_URGENT;
            urgWeight = URG_URGENT;
        } else {
            impWeight = IMP_PEACETIME;
            urgWeight = URG_PEACETIME;
        }

        float base = importance * impWeight + urgency * urgWeight;

        // Convergence bonus
        if (importance > CONVERGENCE_THRESHOLD && urgency > CONVERGENCE_THRESHOLD) {
            base += CONVERGENCE_BONUS;
        }

        // Archetype modifier
        base *= archetype.getGoalModifier(goalType);

        return base;
    }

    // Sub-scorers

    private static float beliefRelevance(StrategicGoal goal, FactionBeliefs beliefs) {
        float best = 0;
        for (FactionBeliefs.BeliefEntry entry : beliefs.getEntries()) {
            BeliefDef def = BeliefRegistry.get(entry.beliefId);
            if (def == null) continue;
            if (goalMatchesBelief(goal, def)) {
                float relevance = entry.strength / 3f;
                if (relevance > best) best = relevance;
            }
        }
        return best;
    }

    private static boolean goalMatchesBelief(StrategicGoal goal, BeliefDef belief) {
        switch (goal.type.category) {
            case EXPANSION:
                return belief.category == BeliefDef.Category.TERRITORIAL
                        || belief.category == BeliefDef.Category.ECONOMIC;
            case SECURITY:
                return belief.category == BeliefDef.Category.MILITARY
                        || belief.category == BeliefDef.Category.POLITICAL;
            case AGGRESSION:
                return belief.category == BeliefDef.Category.MILITARY
                        || belief.category == BeliefDef.Category.IDEOLOGICAL;
            case DIPLOMACY:
                return belief.category == BeliefDef.Category.POLITICAL;
            default:
                return false;
        }
    }

    private static float archetypeAlignment(StrategicGoal goal, Archetype archetype) {
        float mod = archetype.getGoalModifier(goal.type);
        if (mod >= 1.4f) return 1.0f;
        if (mod >= 1.15f) return 0.6f;
        if (mod <= 0.6f) return 0.0f;
        return 0.3f;
    }

    private static float tendencyWeight(StrategicGoal goal, TendencyProfile profile) {
        switch (goal.type.category) {
            case EXPANSION: return profile.get(TendencyId.MILITARISTS) / 5f;
            case SECURITY: return profile.get(TendencyId.INDUSTRIALISTS) / 5f;
            case DIPLOMACY: return profile.get(TendencyId.FEDERALISTS) / 5f;
            case AGGRESSION: return profile.get(TendencyId.MILITARISTS) / 5f;
            case SURVIVAL: return 0.5f;
            case MAINTENANCE: return profile.get(TendencyId.FEDERALISTS) / 5f;
            default: return 0.3f;
        }
    }

    private static float timeConstraint(StrategicGoal goal) {
        if (goal.type == GoalType.RENEW_AGREEMENT) return 0.8f;
        if (goal.type == GoalType.END_WAR) return 0.5f;
        return 0.1f;
    }

    private static float threatProximity(StrategicGoal goal, String factionId) {
        if (goal.type.category == GoalType.Category.SECURITY) return 0.6f;
        if (goal.type.category == GoalType.Category.SURVIVAL) return 0.8f;
        return 0.2f;
    }

    private static float opportunityWindow(StrategicGoal goal, String factionId) {
        if (goal.type == GoalType.EXPLOIT_WEAKNESS) return 0.7f;
        return 0.2f;
    }

    private static float obstacleEscalation(StrategicGoal goal) {
        if (goal.getObstacle().severity > 70) return 0.8f;
        if (goal.getObstacle().severity > 40) return 0.4f;
        return 0.1f;
    }

    private static float cascadeRisk(StrategicGoal goal) {
        return goal.getParentGoalId() != null ? 0.5f : 0.1f;
    }

    private static float getCurrentDay() {
        return com.fs.starfarer.api.Global.getSector().getClock().getTimestamp();
    }
}
