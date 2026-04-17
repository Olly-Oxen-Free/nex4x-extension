package nex4x.ai.goals;

import com.fs.starfarer.api.Global;
import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;

import java.util.List;

/**
 * Personality-gated ambient goals prevent late-game AI passivity.
 * When active goal count < 3, generates low-urgency identity-maintaining goals.
 * See AI spec §10.
 */
public class AmbientGoalGenerator {

    private static final int MIN_GOALS_BEFORE_AMBIENT = 3;

    /**
     * Adds ambient goals to the list if count is below threshold.
     * Called after standard generation.
     */
    public static void addAmbientGoals(String factionId, List<StrategicGoal> goals) {
        if (goals.size() >= MIN_GOALS_BEFORE_AMBIENT) return;

        float currentDay = Global.getSector().getClock().getTimestamp();
        TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
        if (profile == null) return;

        // Dominant tendency drives ambient goal
        TendencyId dominant = getDominantTendency(profile);
        if (dominant == null) return;

        StrategicGoal ambient = null;
        switch (dominant) {
            case MILITARISTS:
                ambient = new StrategicGoal(GoalType.CONTAIN_RIVAL, null, null, currentDay);
                break;
            case FEDERALISTS:
                ambient = new StrategicGoal(GoalType.IMPROVE_RELATIONS, null, null, currentDay);
                break;
            case CORPORATISTS:
                ambient = new StrategicGoal(GoalType.ECONOMIC_DOMINANCE, null, null, currentDay);
                break;
            case ZEALOTS:
                ambient = new StrategicGoal(GoalType.SPREAD_IDEOLOGY, null, null, currentDay);
                break;
            case INDUSTRIALISTS:
                ambient = new StrategicGoal(GoalType.DEFEND_TERRITORY, null, null, currentDay);
                break;
            case ECOLOGISTS:
                ambient = new StrategicGoal(GoalType.MANAGE_PRESSURE, null, null, currentDay);
                break;
            default:
                ambient = new StrategicGoal(GoalType.IMPROVE_RELATIONS, null, null, currentDay);
                break;
        }

        if (ambient != null) {
            ambient.setImportance(20f); // low importance — ambient
            ambient.setUrgency(10f);
            goals.add(ambient);
        }
    }

    private static TendencyId getDominantTendency(TendencyProfile profile) {
        TendencyId best = null;
        float bestVal = 0;
        for (TendencyId t : TendencyId.values()) {
            float v = profile.get(t);
            if (v > bestVal) {
                bestVal = v;
                best = t;
            }
        }
        return best;
    }
}
