package nex4x.agents;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks Nex4x agent companion data keyed by agent PersonAPI id.
 * Keeps it decoupled from the vanilla/Nexerelin Agent intel so modders can
 * attach v3 behavior without forking the Nexerelin agent system.
 */
public class Nex4xAgentManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Global.getLogger(Nex4xAgentManager.class);

    private final Map<String, Nex4xAgentData> agentData = new HashMap<String, Nex4xAgentData>();

    public Nex4xAgentData get(String agentId) { return agentData.get(agentId); }

    public Nex4xAgentData getOrCreate(String agentId, AgentType type) {
        Nex4xAgentData d = agentData.get(agentId);
        if (d == null) {
            d = new Nex4xAgentData(type);
            agentData.put(agentId, d);
            log.info("[Nex4x] Agent data created: " + agentId + " (" + type + ")");
        }
        return d;
    }

    public void remove(String agentId) { agentData.remove(agentId); }

    public List<Nex4xAgentData> getAllData() {
        return new ArrayList<Nex4xAgentData>(agentData.values());
    }

    public Map<String, Nex4xAgentData> getAll() { return agentData; }

    public boolean startRetrain(String agentId, AgentType newType, int agentLevel) {
        Nex4xAgentData d = agentData.get(agentId);
        if (d == null) return false;
        if (d.isRetraining()) return false;
        if (d.getType() == newType) return false;
        d.startRetrain(newType, agentLevel);
        log.info("[Nex4x] Agent " + agentId + " retraining " + d.getType() + " -> " + newType);
        return true;
    }

    public void advanceAll(float days, int defaultAgentLevel) {
        for (Nex4xAgentData d : agentData.values()) {
            d.advanceDay(days, defaultAgentLevel);
        }
    }

    public static Nex4xAgentManager get() {
        Object raw = Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_AGENT_MANAGER);
        if (raw instanceof Nex4xAgentManager) return (Nex4xAgentManager) raw;
        return null;
    }

    public static Nex4xAgentManager getOrCreate() {
        Nex4xAgentManager mgr = get();
        if (mgr == null) {
            mgr = new Nex4xAgentManager();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_AGENT_MANAGER, mgr);
            log.info("[Nex4x] Created Nex4xAgentManager");
        }
        return mgr;
    }

    /**
     * Aggregate diplomat-driven intel score for a target faction in [0, 1].
     * Sums buildup level / max-level across DIPLOMAT-typed agents whose owner is
     * the player faction (so the player's network determines what we know).
     * Caps at 1.0 once the network is dense enough.
     */
    public float getDiplomatIntelScore(String factionId) {
        if (factionId == null) return 0f;
        com.fs.starfarer.api.campaign.SectorAPI sector = Global.getSector();
        if (sector == null) return 0f;
        String playerFid = sector.getPlayerFaction() != null
                ? sector.getPlayerFaction().getId() : null;
        if (playerFid == null) return 0f;

        AgentTypeConfig cfg = AgentTypeConfigLoader.getConfig(AgentType.DIPLOMAT);
        int maxLevel = cfg != null && cfg.influenceOwner != null
                ? Math.max(1, cfg.influenceOwner.length - 1) : 4;

        float score = 0f;
        for (Nex4xAgentData d : agentData.values()) {
            if (d.getType() != AgentType.DIPLOMAT) continue;
            if (!playerFid.equals(d.getOwnerFactionId())) continue;
            if (d.isRetraining()) continue;
            // Diplomats under cascade suspicion in this faction's space lose visibility.
            if (d.hasCascadeSuspicion(factionId)) continue;
            int level = d.getBuildupTracker().getLevel();
            score += (float) level / (float) maxLevel;
        }
        return Math.max(0f, Math.min(1f, score));
    }
}
