package nex4x.ai.goals;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;

/**
 * Filters goals by feasibility. Goals with zero feasibility are excluded.
 * See AI spec §2.6.
 */
public class FeasibilityChecker {

    /**
     * Returns feasibility score 0-1. Goals scoring 0 are excluded.
     */
    public static float check(StrategicGoal goal, String factionId) {
        switch (goal.type) {
            case CLAIM_TERRITORY:
            case PRESS_GRIEVANCE:
            case EXPLOIT_WEAKNESS:
                return militaryFeasibility(goal, factionId);
            case BUILD_ALLIANCE:
            case SECURE_AGREEMENT:
            case IMPROVE_RELATIONS:
                return diplomaticFeasibility(goal, factionId);
            default:
                return 1.0f;
        }
    }

    private static float militaryFeasibility(StrategicGoal goal, String factionId) {
        if (goal.targetFactionId == null) return 0.5f;

        float ourStrength = estimateMilitaryStrength(factionId);
        float theirStrength = estimateMilitaryStrength(goal.targetFactionId);

        if (theirStrength <= 0) return 1.0f;
        float ratio = ourStrength / theirStrength;

        float feasibility;
        if (ratio < 0.7f) feasibility = 0.1f;
        else if (ratio < 1.0f) feasibility = 0.3f;
        else if (ratio < 1.5f) feasibility = 0.7f;
        else feasibility = 1.0f;

        FactionAPI target = Global.getSector().getFaction(goal.targetFactionId);
        if (target != null) {
            int targetWars = 0;
            for (FactionAPI f : Global.getSector().getAllFactions()) {
                if (target.isHostileTo(f) && !f.isNeutralFaction()) targetWars++;
            }
            if (targetWars > 0) feasibility *= 1.3f;
        }

        return Math.min(1f, feasibility);
    }

    private static float diplomaticFeasibility(StrategicGoal goal, String factionId) {
        if (goal.targetFactionId == null) return 0.5f;

        FactionAPI us = Global.getSector().getFaction(factionId);
        if (us == null) return 0.5f;
        float rel = us.getRelationship(goal.targetFactionId);

        if (rel < -0.5f) return 0.1f;
        if (rel < 0f) return 0.3f;
        return 0.8f;
    }

    private static float estimateMilitaryStrength(String factionId) {
        try {
            return exerelin.utilities.NexUtilsFaction.getFactionMarketSizeSum(factionId);
        } catch (Exception e) {
            return 10f;
        }
    }
}
