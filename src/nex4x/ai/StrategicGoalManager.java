package nex4x.ai;

import com.fs.starfarer.api.Global;
import nex4x.ai.archetype.Archetype;
import nex4x.ai.archetype.CommitmentLedger;
import nex4x.ai.archetype.GrandStrategyManager;
import nex4x.ai.goals.AmbientGoalGenerator;
import nex4x.ai.goals.FeasibilityChecker;
import nex4x.ai.goals.GoalGenerator;
import nex4x.ai.goals.GoalScorer;
import nex4x.ai.goals.GoalType;
import nex4x.ai.goals.StrategicGoal;
import nex4x.ai.posture.PostureMap;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Runs daily per faction. Generates goals, scores them, identifies obstacles,
 * updates focus and posture. See AI spec §2.
 */
public class StrategicGoalManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(StrategicGoalManager.class);

    private static int MAX_ACTIVE_GOALS = 8;

    private final String factionId;
    private final List<StrategicGoal> activeGoals = new ArrayList<StrategicGoal>();
    private final StrategicFocus focus = new StrategicFocus();
    private final PostureMap postureMap = new PostureMap();

    private float lastImportanceCalcDay = 0;
    private static final float IMPORTANCE_RECALC_INTERVAL = 7f;

    public static void loadConfig(JSONObject config) {
        MAX_ACTIVE_GOALS = config.optInt("maxActiveGoals", 8);
        GoalScorer.loadConfig(config);
    }

    public StrategicGoalManager(String factionId) {
        this.factionId = factionId;
    }

    /**
     * Daily update. Called by Nex4xManager.advance().
     */
    public void advanceDay(GrandStrategyManager grandStrategy) {
        float currentDay = Global.getSector().getClock().getTimestamp();
        CommitmentLedger ledger = grandStrategy.getLedger(factionId);
        Archetype archetype = ledger.getCurrentArchetype();

        // 1. Generate candidate goals
        List<StrategicGoal> candidates = GoalGenerator.generateGoals(factionId);

        // 2. Add ambient goals if low
        AmbientGoalGenerator.addAmbientGoals(factionId, candidates);

        // 3. Filter by feasibility
        Iterator<StrategicGoal> it = candidates.iterator();
        while (it.hasNext()) {
            StrategicGoal g = it.next();
            if (FeasibilityChecker.check(g, factionId) < 0.1f) {
                it.remove();
            }
        }

        // 4. Score importance (weekly) and urgency (daily)
        boolean recalcImportance = (currentDay - lastImportanceCalcDay) >= IMPORTANCE_RECALC_INTERVAL;
        if (recalcImportance) lastImportanceCalcDay = currentDay;

        Map<String, StrategicGoal> existingByKey = new HashMap<String, StrategicGoal>();
        for (StrategicGoal g : activeGoals) {
            existingByKey.put(g.getKey(), g);
        }

        for (StrategicGoal candidate : candidates) {
            StrategicGoal existing = existingByKey.get(candidate.getKey());
            if (existing != null) {
                if (recalcImportance) {
                    existing.setImportance(GoalScorer.scoreImportance(existing, factionId, ledger));
                }
                existing.setUrgency(GoalScorer.scoreUrgency(existing, factionId));
            } else {
                candidate.setImportance(GoalScorer.scoreImportance(candidate, factionId, ledger));
                candidate.setUrgency(GoalScorer.scoreUrgency(candidate, factionId));
                existingByKey.put(candidate.getKey(), candidate);
            }
        }

        // 5. Compute effective priority
        boolean hasCrisis = focus.getGreatestThreat() != null
                && focus.getGreatestThreat().existential;
        boolean hasUrgentGoal = false;
        for (StrategicGoal g : existingByKey.values()) {
            if (g.getUrgency() > 70) { hasUrgentGoal = true; break; }
        }

        for (StrategicGoal g : existingByKey.values()) {
            float eff = GoalScorer.computeEffectivePriority(
                    g.getImportance(), g.getUrgency(),
                    hasCrisis, hasUrgentGoal,
                    archetype, g.type);
            g.setEffectivePriority(eff);
        }

        // 6. Rank and keep top N
        List<StrategicGoal> ranked = new ArrayList<StrategicGoal>(existingByKey.values());
        Collections.sort(ranked, new Comparator<StrategicGoal>() {
            public int compare(StrategicGoal a, StrategicGoal b) {
                return Float.compare(b.getEffectivePriority(), a.getEffectivePriority());
            }
        });

        activeGoals.clear();
        for (int i = 0; i < Math.min(MAX_ACTIVE_GOALS, ranked.size()); i++) {
            activeGoals.add(ranked.get(i));
        }

        // 7. Update focus
        StrategicGoal highestImportance = null;
        float bestImp = -1;
        for (StrategicGoal g : activeGoals) {
            if (g.getImportance() > bestImp) {
                bestImp = g.getImportance();
                highestImportance = g;
            }
        }

        StrategicGoal highestPriority = activeGoals.isEmpty() ? null : activeGoals.get(0);
        focus.update(highestImportance, highestPriority, focus.getGreatestThreat(), 1f);

        // 8. Update posture map
        postureMap.rebuild(activeGoals);

        // 9. Feed grand strategy
        StrategicFocus.Threat threat = focus.getGreatestThreat();
        grandStrategy.advanceDay(factionId, activeGoals, highestImportance,
                threat != null ? threat.severity : 0,
                threat != null ? threatToArchetype(threat) : null);
    }

    private Archetype threatToArchetype(StrategicFocus.Threat threat) {
        switch (threat.type) {
            case MILITARY_AGGRESSION: return Archetype.MILITARY_SUPREMACY;
            case ECONOMIC_COMPETITION: return Archetype.ECONOMIC_HEGEMONY;
            case TERRITORIAL_ENCROACHMENT: return Archetype.TERRITORIAL_EXPANSION;
            case COALITION_FORMATION: return Archetype.COALITION_BUILDER;
            case IDEOLOGICAL_SPREAD: return Archetype.IDEOLOGICAL_CRUSADE;
            default: return Archetype.DEFENSIVE_CONSOLIDATION;
        }
    }

    // Getters
    public List<StrategicGoal> getActiveGoals() {
        return Collections.unmodifiableList(activeGoals);
    }
    public StrategicFocus getFocus() { return focus; }
    public PostureMap getPostureMap() { return postureMap; }
    public String getFactionId() { return factionId; }
}
