package nex4x.agents.actions;

import nex4x.agents.AgentType;

import java.io.Serializable;

/** Config for a single agent action. */
public class ActionConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String id;
    public final AgentType requiredType;
    public final int minLevel;
    public final long creditCost;
    public final float influenceCost;
    public final int durationDays;
    public final int cooldownDays;
    public final float relationshipPenaltyOnDetection;

    public ActionConfig(String id, AgentType requiredType, int minLevel,
                        long creditCost, float influenceCost,
                        int durationDays, int cooldownDays,
                        float relationshipPenaltyOnDetection) {
        this.id = id;
        this.requiredType = requiredType;
        this.minLevel = minLevel;
        this.creditCost = creditCost;
        this.influenceCost = influenceCost;
        this.durationDays = durationDays;
        this.cooldownDays = cooldownDays;
        this.relationshipPenaltyOnDetection = relationshipPenaltyOnDetection;
    }
}
