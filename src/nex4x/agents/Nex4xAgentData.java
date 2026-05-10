package nex4x.agents;

import java.io.Serializable;

/**
 * Per-agent v3 companion data. Stored in Nex4xAgentManager keyed by PersonAPI id.
 * Holds type, buildup, synergy state, diplomat expulsion data.
 */
public class Nex4xAgentData implements Serializable {
    private static final long serialVersionUID = 1L;

    private AgentType type;
    /** Faction id of the agent's owner (faction that deployed the agent). May be null on legacy entries. */
    private String ownerFactionId;
    private final BuildupTracker buildupTracker;

    // Retraining state
    private boolean isRetraining;
    private float retrainDaysRemaining;
    private AgentType retrainTargetType;

    // Guerrilla synergy activation
    private boolean synergyActive;
    private float synergyDaysRemaining;

    // Diplomat expulsion
    private float expulsionCooldownDays;
    private String expulsionMarketId;

    // Diplomat cascade suspicion (applied to all diplomats of this faction with target)
    private float cascadeSuspicionDays;
    private String cascadeTargetFactionId;

    public Nex4xAgentData(AgentType type) {
        this.type = type;
        this.buildupTracker = new BuildupTracker();
        this.isRetraining = false;
        this.synergyActive = false;
        this.expulsionCooldownDays = 0;
    }

    // Type
    public AgentType getType() { return type; }
    public void setType(AgentType type) { this.type = type; }

    public String getOwnerFactionId() { return ownerFactionId; }
    public void setOwnerFactionId(String id) { this.ownerFactionId = id; }

    // Buildup
    public BuildupTracker getBuildupTracker() { return buildupTracker; }

    // Retraining
    public boolean isRetraining() { return isRetraining; }
    public float getRetrainDaysRemaining() { return retrainDaysRemaining; }
    public AgentType getRetrainTargetType() { return retrainTargetType; }

    public void startRetrain(AgentType targetType, int agentLevel) {
        AgentTypeConfig config = AgentTypeConfigLoader.getConfig(type);
        if (config == null) return;
        this.isRetraining = true;
        this.retrainTargetType = targetType;
        this.retrainDaysRemaining = config.retrainBaseDays + config.retrainDaysPerLevel * agentLevel;
    }

    public void advanceRetrain(float days) {
        if (!isRetraining) return;
        retrainDaysRemaining -= days;
        if (retrainDaysRemaining <= 0) {
            type = retrainTargetType;
            buildupTracker.reset();
            isRetraining = false;
            retrainTargetType = null;
        }
    }

    // Guerrilla synergy
    public boolean isSynergyActive() { return synergyActive; }
    public void activateSynergy(float durationDays) {
        synergyActive = true;
        synergyDaysRemaining = Math.max(synergyDaysRemaining, durationDays);
    }
    public void advanceSynergy(float days) {
        if (!synergyActive) return;
        synergyDaysRemaining -= days;
        if (synergyDaysRemaining <= 0) {
            synergyActive = false;
            synergyDaysRemaining = 0;
        }
    }

    // Diplomat expulsion
    public boolean isExpelledFrom(String marketId) {
        return expulsionCooldownDays > 0 && marketId.equals(expulsionMarketId);
    }
    public void setExpulsion(String marketId, float cooldownDays) {
        this.expulsionMarketId = marketId;
        this.expulsionCooldownDays = cooldownDays;
    }
    public void advanceExpulsion(float days) {
        if (expulsionCooldownDays > 0) {
            expulsionCooldownDays -= days;
            if (expulsionCooldownDays <= 0) {
                expulsionCooldownDays = 0;
                expulsionMarketId = null;
            }
        }
    }

    // Cascade suspicion
    public boolean hasCascadeSuspicion(String factionId) {
        return cascadeSuspicionDays > 0 && factionId.equals(cascadeTargetFactionId);
    }
    public float getCascadeSuspicionDays() { return cascadeSuspicionDays; }
    public void setCascadeSuspicion(String factionId, float days) {
        this.cascadeTargetFactionId = factionId;
        this.cascadeSuspicionDays = days;
    }
    public void advanceCascade(float days) {
        if (cascadeSuspicionDays > 0) {
            cascadeSuspicionDays -= days;
            if (cascadeSuspicionDays <= 0) {
                cascadeSuspicionDays = 0;
                cascadeTargetFactionId = null;
            }
        }
    }

    /** Full daily tick. */
    public void advanceDay(float days, int agentLevel) {
        AgentTypeConfig config = AgentTypeConfigLoader.getConfig(type);
        if (config == null) return;

        if (isRetraining) {
            advanceRetrain(days);
        } else {
            buildupTracker.advanceDay(config.buildupRatePerDay * days, agentLevel);
            buildupTracker.applyDecay(config.buildupDecayPerDay * days);
        }
        advanceSynergy(days);
        advanceExpulsion(days);
        advanceCascade(days);
    }
}
