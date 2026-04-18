package nex4x.agents.actions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.AgentType;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.security.DetectionEngine;
import org.apache.log4j.Logger;

import java.io.Serializable;

/**
 * Abstract base class for all v3 agent actions.
 * Subclasses supply effect + detection fallout; base handles detection roll and logging.
 */
public abstract class BaseAgentAction implements Serializable {
    private static final long serialVersionUID = 1L;

    protected static final Logger log = Global.getLogger(BaseAgentAction.class);

    protected final String agentId;
    protected final String actorFactionId;
    protected final String targetMarketId;
    protected final ActionConfig config;
    protected final int agentLevel;

    protected boolean detected;
    protected boolean resolved;
    protected float daysRemaining;

    public BaseAgentAction(String agentId, String actorFactionId, String targetMarketId,
                           ActionConfig config, int agentLevel) {
        this.agentId = agentId;
        this.actorFactionId = actorFactionId;
        this.targetMarketId = targetMarketId;
        this.config = config;
        this.agentLevel = agentLevel;
        this.daysRemaining = config.durationDays;
    }

    /** Called once when duration elapses. Subclasses apply effect. */
    protected abstract void applyEffect(Nex4xAgentData data, MarketAPI market);

    /** Called on detection (if detected). Subclasses apply detection fallout. */
    protected abstract void applyDetectionFallout(Nex4xAgentData data, MarketAPI market);

    /** Called daily; default handles countdown + detection roll on resolve. */
    public void advanceDay(float days, Nex4xAgentData data, AgentType type) {
        if (resolved) return;
        daysRemaining -= days;
        if (daysRemaining <= 0) resolve(data, type);
    }

    private void resolve(Nex4xAgentData data, AgentType type) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(targetMarketId);
        if (market == null) {
            resolved = true;
            return;
        }
        float attacker = DetectionEngine.getAttackerStrength(type, data.getBuildupTracker());
        float defender = DetectionEngine.getMarketDetection(market);
        detected = DetectionEngine.rollDetection(attacker, defender);

        applyEffect(data, market);
        if (detected) {
            log.info("[Nex4x] Action " + config.id + " by agent " + agentId
                    + " DETECTED on " + targetMarketId);
            applyDetectionFallout(data, market);
        } else {
            log.info("[Nex4x] Action " + config.id + " by agent " + agentId
                    + " succeeded undetected on " + targetMarketId);
        }
        resolved = true;
    }

    public boolean isResolved() { return resolved; }
    public boolean isDetected() { return detected; }
    public String getAgentId() { return agentId; }
    public String getTargetMarketId() { return targetMarketId; }
    public String getActorFactionId() { return actorFactionId; }
    public ActionConfig getConfig() { return config; }
    public float getDaysRemaining() { return daysRemaining; }
}
