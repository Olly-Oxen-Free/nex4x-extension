package nex4x.agents;

import java.io.Serializable;

/**
 * Tracks buildup progress for one agent at their current market.
 * Resets to 0 on market move or type switch.
 */
public class BuildupTracker implements Serializable {
    private static final long serialVersionUID = 1L;

    private float points;
    private String marketId;   // market where buildup was earned

    public BuildupTracker() {
        this.points = 0f;
        this.marketId = null;
    }

    public float getPoints() { return points; }
    public int getLevel() { return BuildupLevel.getLevelForPoints(points); }
    public String getMarketId() { return marketId; }

    /** Advance buildup by one day. Accounts for agent level bonus. */
    public void advanceDay(float baseRate, int agentLevel) {
        float levelMult = 1.0f + (agentLevel - 1) * 0.1f;
        points += baseRate * levelMult;
        float maxPoints = BuildupLevel.THRESHOLDS[BuildupLevel.MAX_LEVEL] + 30f;
        if (points > maxPoints) points = maxPoints;
    }

    /** Apply daily decay (idle or security increase). */
    public void applyDecay(float decayRate) {
        points -= decayRate;
        if (points < 0) points = 0;
    }

    /** Boost from Embed action or official diplomat actions. */
    public void addBoost(float amount) {
        points += amount;
    }

    /** Reset on market move or type switch. */
    public void reset() {
        points = 0f;
        marketId = null;
    }

    /** Set market (called when agent arrives). */
    public void setMarket(String marketId) {
        if (this.marketId != null && !this.marketId.equals(marketId)) {
            reset();
        }
        this.marketId = marketId;
    }

    /** Damage standing (diplomat cascade: -1 level). */
    public void damageByOneLevel() {
        int currentLevel = getLevel();
        if (currentLevel <= 0) return;
        points = BuildupLevel.THRESHOLDS[currentLevel - 1];
    }

    public float getProgressToNextLevel() {
        int level = getLevel();
        if (level >= BuildupLevel.MAX_LEVEL) return 1.0f;
        float current = points - BuildupLevel.THRESHOLDS[level];
        float needed = BuildupLevel.THRESHOLDS[level + 1] - BuildupLevel.THRESHOLDS[level];
        return current / needed;
    }
}
