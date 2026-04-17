package nex4x.wargoals;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tracks war score per active war.
 * War score = battles won/lost + markets captured/lost + fleet attrition.
 * Used to determine peace willingness and available peace terms.
 */
public class WarScoreTracker implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(WarScoreTracker.class);

    /** War score keyed by "factionA:factionB" (canonical — alphabetical order). */
    private final Map<String, Float> warScores = new HashMap<String, Float>();
    private final List<WarGoal> warGoals = new ArrayList<WarGoal>();

    // Score weights
    private static final float BATTLE_WIN = 5f;
    private static final float BATTLE_LOSS = -3f;
    private static final float MARKET_CAPTURE = 20f;
    private static final float MARKET_LOSS = -15f;

    /** Create a war goal when war is declared. */
    public WarGoal createWarGoal(String holderFactionId, String targetFactionId,
                                  WarGoalType type, String targetMarketId) {
        float currentDay = Global.getSector().getClock().getTimestamp();
        WarGoal goal = new WarGoal(holderFactionId, targetFactionId, type,
                targetMarketId, currentDay);
        warGoals.add(goal);

        // Initialize war score if not present
        String key = warKey(holderFactionId, targetFactionId);
        if (!warScores.containsKey(key)) {
            warScores.put(key, 0f);
        }

        log.info("[Nex4x] War goal created: " + holderFactionId + " -> " + targetFactionId
                + " (" + type.displayName + ")");
        return goal;
    }

    /** Called when a battle resolves. */
    public void reportBattle(String winnerFactionId, String loserFactionId) {
        adjustScore(winnerFactionId, loserFactionId, BATTLE_WIN);
        adjustScore(loserFactionId, winnerFactionId, BATTLE_LOSS);
    }

    /** Called when a market changes faction. */
    public void reportMarketChange(String captorFactionId, String previousFactionId,
                                    String marketId) {
        adjustScore(captorFactionId, previousFactionId, MARKET_CAPTURE);
        adjustScore(previousFactionId, captorFactionId, MARKET_LOSS);

        // Update territorial war goals
        for (WarGoal goal : warGoals) {
            if (goal.isActive()
                    && goal.getType() == WarGoalType.TERRITORIAL_CLAIM
                    && marketId.equals(goal.getTargetMarketId())
                    && captorFactionId.equals(goal.getHolderFactionId())) {
                goal.setScopeProgress(1.0f);
                log.info("[Nex4x] War goal achieved: " + goal.getKey());
            }
        }
    }

    /** Get war score for a faction in a war against another. Positive = winning. */
    public float getWarScore(String factionId, String againstFactionId) {
        String key = warKey(factionId, againstFactionId);
        Float score = warScores.get(key);
        if (score == null) return 0;

        // Positive means factionId is winning (alphabetical-first faction's perspective)
        String[] parts = key.split(":");
        if (parts[0].equals(factionId)) return score;
        return -score;
    }

    public List<WarGoal> getActiveGoals(String factionId) {
        List<WarGoal> result = new ArrayList<WarGoal>();
        for (WarGoal g : warGoals) {
            if (g.isActive() && g.getHolderFactionId().equals(factionId)) {
                result.add(g);
            }
        }
        return result;
    }

    public List<WarGoal> getWarGoals(String factionId, String targetFactionId) {
        List<WarGoal> result = new ArrayList<WarGoal>();
        for (WarGoal g : warGoals) {
            if (g.isActive()
                    && g.getHolderFactionId().equals(factionId)
                    && g.getTargetFactionId().equals(targetFactionId)) {
                result.add(g);
            }
        }
        return result;
    }

    /** Daily advance — prune inactive goals, reset scores for ended wars. */
    public void advanceDay() {
        // Prune inactive war goals
        Iterator<WarGoal> it = warGoals.iterator();
        while (it.hasNext()) {
            WarGoal g = it.next();
            if (!g.isActive()) it.remove();
        }

        // Prune war scores for ended wars
        Iterator<Map.Entry<String, Float>> scoreIt = warScores.entrySet().iterator();
        while (scoreIt.hasNext()) {
            Map.Entry<String, Float> entry = scoreIt.next();
            String[] parts = entry.getKey().split(":");
            if (parts.length == 2) {
                FactionAPI a = Global.getSector().getFaction(parts[0]);
                FactionAPI b = Global.getSector().getFaction(parts[1]);
                if (a != null && b != null && !a.isHostileTo(b)) {
                    scoreIt.remove();
                }
            }
        }
    }

    private void adjustScore(String winner, String loser, float delta) {
        String key = warKey(winner, loser);
        float current = warScores.containsKey(key) ? warScores.get(key) : 0f;

        // If winner comes first alphabetically, positive score means winner is winning
        String[] parts = key.split(":");
        if (parts[0].equals(winner)) {
            warScores.put(key, current + delta);
        } else {
            warScores.put(key, current - delta);
        }
    }

    private String warKey(String factionA, String factionB) {
        if (factionA.compareTo(factionB) <= 0) {
            return factionA + ":" + factionB;
        } else {
            return factionB + ":" + factionA;
        }
    }
}
