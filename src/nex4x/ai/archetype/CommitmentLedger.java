package nex4x.ai.archetype;

import com.fs.starfarer.api.Global;
import nex4x.ai.goals.GoalType;
import nex4x.ai.goals.StrategicGoal;
import nex4x.data.BeliefDef;
import nex4x.data.FactionBeliefs;
import nex4x.data.FactionBeliefsLoader;
import nex4x.data.TendencyId;
import nex4x.data.TendencyProfile;
import nex4x.data.TendencyProfileLoader;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-faction archetype commitment tracking.
 * Scores accumulate from goals and beliefs, decay over time,
 * and drive archetype transitions with hysteresis.
 */
public class CommitmentLedger implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(CommitmentLedger.class);

    private final String factionId;
    private final Map<Archetype, Float> scores = new EnumMap<Archetype, Float>(Archetype.class);
    private Archetype currentArchetype;
    /** Timestamp (game-seconds) when current archetype was set. */
    private long archetypeSinceTs;
    /** Timestamp (game-seconds) when crisis lock expires; 0 = no lock. */
    private long lockExpiryTs;
    /** One-day in game-seconds; matches engine convention used by getElapsedDaysSince. */
    private static final long SECONDS_PER_DAY = 86400L;

    // Config values (loaded from grand_strategy.json)
    private static float TRANSITION_THRESHOLD = 0.20f;
    private static int MIN_TREND_DAYS = 30;
    private static float TRANSITION_BOOST = 1.15f;
    private static float DAILY_DECAY = 0.997f;
    private static float DOMINANT_DECAY = 0.999f;
    private static float GOAL_FEED_RATE = 0.01f;
    private static float BELIEF_FLOOR_RATE = 0.005f;
    private static float OPPORTUNIST_THRESHOLD = 10f;

    public static void loadConfig(JSONObject config) {
        TRANSITION_THRESHOLD = (float) config.optDouble("transitionThreshold", 0.20);
        MIN_TREND_DAYS = config.optInt("minTrendDays", 30);
        TRANSITION_BOOST = (float) config.optDouble("transitionBoost", 1.15);
        DAILY_DECAY = (float) config.optDouble("dailyDecay", 0.997);
        DOMINANT_DECAY = (float) config.optDouble("dominantDecay", 0.999);
        GOAL_FEED_RATE = (float) config.optDouble("goalFeedRate", 0.01);
        BELIEF_FLOOR_RATE = (float) config.optDouble("beliefFloorRate", 0.005);
        OPPORTUNIST_THRESHOLD = (float) config.optDouble("opportunistThreshold", 10);
    }

    public CommitmentLedger(String factionId) {
        this.factionId = factionId;
        for (Archetype a : Archetype.values()) {
            scores.put(a, 0f);
        }
    }

    /** Initialize from faction personality. Called once on first encounter. */
    public void initialize() {
        TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
        if (profile == null) return;

        for (Archetype archetype : Archetype.values()) {
            if (archetype == Archetype.OPPORTUNIST) continue;
            float score = 0;
            for (TendencyId t : TendencyId.values()) {
                score += archetype.getTendencyAffinity(t) * profile.get(t);
            }
            scores.put(archetype, score);
        }

        // Normalize so dominant is ~60
        float max = 0;
        for (float s : scores.values()) {
            if (s > max) max = s;
        }
        if (max > 0) {
            float scale = 60f / max;
            for (Archetype a : Archetype.values()) {
                scores.put(a, scores.get(a) * scale);
            }
        }

        Archetype resolved = resolveArchetype();
        Archetype forced = ArchetypeOverrideRegistry.getForced(factionId);
        currentArchetype = forced != null ? forced : resolved;
        archetypeSinceTs = nex4x.util.Nex4xClock.now();
        log.info("[Nex4x] " + factionId + " initialized archetype: " + currentArchetype.displayName);
    }

    /** Daily update — feed from goals, apply decay, check transition. */
    public void advanceDay(List<StrategicGoal> activeGoals, StrategicGoal strategicObjective,
                           float greatestThreatSeverity, Archetype threatArchetype) {
        long nowTs = nex4x.util.Nex4xClock.now();

        // 1. Goal contributions
        for (StrategicGoal goal : activeGoals) {
            Archetype primary = goal.type.getPrimaryArchetype();
            Archetype secondary = goal.type.getSecondaryArchetype();

            if (primary != null) {
                float feed = goal.getEffectivePriority() * GOAL_FEED_RATE;
                if (goal == strategicObjective) feed *= 2f;
                scores.put(primary, scores.get(primary) + feed);
            }
            if (secondary != null) {
                float feed = goal.getEffectivePriority() * GOAL_FEED_RATE * 0.3f;
                scores.put(secondary, scores.get(secondary) + feed);
            }
        }

        // 2. Belief floor
        FactionBeliefs beliefs = FactionBeliefsLoader.getBeliefs(factionId);
        if (beliefs != null) {
            for (FactionBeliefs.BeliefEntry entry : beliefs.getEntries()) {
                BeliefDef def = nex4x.data.BeliefRegistry.get(entry.beliefId);
                if (def == null) continue;
                Archetype beliefArch = beliefCategoryToArchetype(def.category);
                if (beliefArch != null) {
                    scores.put(beliefArch,
                            scores.get(beliefArch) + entry.strength * BELIEF_FLOOR_RATE);
                }
            }
        }

        // 3. Threat contribution
        if (threatArchetype != null && greatestThreatSeverity > 0) {
            scores.put(threatArchetype,
                    scores.get(threatArchetype) + greatestThreatSeverity * 0.005f);
        }

        // 4. Decay
        for (Archetype a : Archetype.values()) {
            if (a == Archetype.OPPORTUNIST) continue;
            float decay = (a == currentArchetype) ? DOMINANT_DECAY : DAILY_DECAY;
            scores.put(a, scores.get(a) * decay);
        }

        // 5. Check transition (skip if crisis-locked)
        if (lockExpiryTs == 0L || nowTs >= lockExpiryTs) {
            checkTransition(nowTs);
        }
    }

    private void checkTransition(long nowTs) {
        Archetype challenger = null;
        float challengerScore = 0;
        for (Archetype a : Archetype.values()) {
            if (a == Archetype.OPPORTUNIST || a == currentArchetype) continue;
            if (scores.get(a) > challengerScore) {
                challengerScore = scores.get(a);
                challenger = a;
            }
        }
        if (challenger == null) return;

        float currentScore = scores.get(currentArchetype);
        float margin = challengerScore - currentScore;
        if (margin <= 0) return;

        float threshold = currentScore * TRANSITION_THRESHOLD;
        float trendDays = nex4x.util.Nex4xClock.daysSince(archetypeSinceTs);

        if (margin > threshold && trendDays >= MIN_TREND_DAYS) {
            transitionTo(challenger, nowTs);
        }
    }

    private void transitionTo(Archetype newArchetype, long nowTs) {
        log.info("[Nex4x] " + factionId + " archetype transition: "
                + currentArchetype.displayName + " -> " + newArchetype.displayName);
        currentArchetype = newArchetype;
        archetypeSinceTs = nowTs;
        scores.put(newArchetype, scores.get(newArchetype) * TRANSITION_BOOST);
    }

    /** Force transition for crisis events. Bypasses hysteresis. */
    public void forceTransition(Archetype archetype, int lockDays) {
        long nowTs = nex4x.util.Nex4xClock.now();
        currentArchetype = archetype;
        archetypeSinceTs = nowTs;
        lockExpiryTs = nowTs + (long) lockDays * SECONDS_PER_DAY;
        log.info("[Nex4x] " + factionId + " CRISIS transition -> "
                + archetype.displayName + " (locked " + lockDays + " days)");
    }

    private Archetype resolveArchetype() {
        Archetype best = Archetype.OPPORTUNIST;
        Archetype second = Archetype.OPPORTUNIST;
        float bestScore = 0, secondScore = 0;

        for (Archetype a : Archetype.values()) {
            if (a == Archetype.OPPORTUNIST) continue;
            float s = scores.get(a);
            if (s > bestScore) {
                second = best;
                secondScore = bestScore;
                best = a;
                bestScore = s;
            } else if (s > secondScore) {
                second = a;
                secondScore = s;
            }
        }

        if (best == Archetype.OPPORTUNIST) {
            return Archetype.OPPORTUNIST;
        }

        if (bestScore - secondScore < OPPORTUNIST_THRESHOLD) {
            TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
            if (profile != null) {
                Archetype pick = null;
                float bestAlign = -1f;
                for (Archetype a : Archetype.values()) {
                    if (a == Archetype.OPPORTUNIST) continue;
                    float s = scores.get(a);
                    if (s + 0.01f < bestScore) continue;
                    float align = tendencyAlignment(a, profile);
                    if (align > bestAlign) {
                        bestAlign = align;
                        pick = a;
                    } else if (Math.abs(align - bestAlign) < 0.001f && pick != null
                            && a.ordinal() < pick.ordinal()) {
                        pick = a;
                    }
                }
                if (pick != null) {
                    return pick;
                }
            }
        }
        return best;
    }

    private static float tendencyAlignment(Archetype a, TendencyProfile p) {
        float sum = 0f;
        for (TendencyId t : TendencyId.values()) {
            sum += a.getTendencyAffinity(t) * p.get(t);
        }
        return sum;
    }

    private Archetype beliefCategoryToArchetype(BeliefDef.Category cat) {
        switch (cat) {
            case TERRITORIAL: return Archetype.TERRITORIAL_EXPANSION;
            case ECONOMIC: return Archetype.ECONOMIC_HEGEMONY;
            case POLITICAL: return Archetype.COALITION_BUILDER;
            case IDEOLOGICAL: return Archetype.IDEOLOGICAL_CRUSADE;
            case MILITARY: return Archetype.MILITARY_SUPREMACY;
            default: return null;
        }
    }

    // Getters
    public Archetype getCurrentArchetype() { return currentArchetype; }
    public float getScore(Archetype a) { return scores.containsKey(a) ? scores.get(a) : 0; }
    public String getFactionId() { return factionId; }
    public boolean isLocked() {
        return lockExpiryTs > 0L && nex4x.util.Nex4xClock.now() < lockExpiryTs;
    }
}
