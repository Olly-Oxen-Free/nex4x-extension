package nex4x.strategic.concern;

import com.fs.starfarer.api.Global;
import exerelin.campaign.ai.StrategicAIModule;
import exerelin.campaign.ai.concern.BaseStrategicConcern;
import exerelin.campaign.ai.concern.StrategicConcern;
import nex4x.ai.StrategicGoalManager;
import nex4x.ai.goals.StrategicGoal;
import nex4x.managers.Nex4xManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wraps a Nex4x StrategicGoal as a Nex StrategicConcern so the Nex StrategicAI
 * UI can present our reasoning. Read-only: no decisions made here.
 */
public class GoalWrapConcern extends BaseStrategicConcern {

    private StrategicGoal sourceGoal;

    @Override
    public boolean generate() {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null || ai == null) return false;
        String factionId = ai.getFactionId();
        StrategicGoalManager gm = mgr.getGoalManager(factionId);
        if (gm == null) return false;

        List<StrategicGoal> goals = gm.getActiveGoals();
        if (goals.isEmpty()) return false;

        Set<String> wrapped = wrappedGoalKeys();
        StrategicGoal best = null;
        for (StrategicGoal g : goals) {
            if (wrapped.contains(g.getKey())) continue;
            if (best == null || g.getEffectivePriority() > best.getEffectivePriority()) {
                best = g;
            }
        }
        if (best == null) return false;

        sourceGoal = best;
        if (best.targetFactionId != null) {
            faction = Global.getSector().getFaction(best.targetFactionId);
        }
        return true;
    }

    private Set<String> wrappedGoalKeys() {
        Set<String> keys = new HashSet<String>();
        collectKeys(ai.getEconModule(), keys);
        collectKeys(ai.getMilModule(), keys);
        collectKeys(ai.getDiploModule(), keys);
        return keys;
    }

    private void collectKeys(StrategicAIModule mod, Set<String> keys) {
        if (mod == null) return;
        for (StrategicConcern c : mod.getCurrentConcerns()) {
            if (c instanceof GoalWrapConcern) {
                StrategicGoal g = ((GoalWrapConcern) c).sourceGoal;
                if (g != null) keys.add(g.getKey());
            }
        }
    }

    @Override
    public void update() {
        if (sourceGoal == null) return;
        priority.modifyFlat("nex4x_goal",
                sourceGoal.getEffectivePriority(),
                "Goal: " + sourceGoal.type.displayName);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Set getExistingConcernItems() {
        Set s = new HashSet();
        if (sourceGoal != null) s.add(sourceGoal.getKey());
        return s;
    }

    public StrategicGoal getSourceGoal() { return sourceGoal; }

    @Override
    public boolean isValid() {
        if (sourceGoal == null) return false;
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null || ai == null) return false;
        StrategicGoalManager gm = mgr.getGoalManager(ai.getFactionId());
        if (gm == null) return false;
        for (StrategicGoal g : gm.getActiveGoals()) {
            if (g.getKey().equals(sourceGoal.getKey())) return true;
        }
        return false;
    }
}
