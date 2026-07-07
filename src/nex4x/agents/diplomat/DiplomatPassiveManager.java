package nex4x.agents.diplomat;

import com.fs.starfarer.api.Global;
import nex4x.agents.AgentType;
import nex4x.agents.AgentTypeConfig;
import nex4x.agents.AgentTypeConfigLoader;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.Nex4xAgentManager;
import nex4x.influence.InfluenceManager;
import nex4x.influence.InfluenceSource;
import org.apache.log4j.Logger;

/**
 * Daily sweep: diplomats passively generate influence for their owning faction
 * based on their standing buildup level at the host market.
 */
public class DiplomatPassiveManager {
    private static final Logger log = Global.getLogger(DiplomatPassiveManager.class);

    public static void advanceDay(float days, String ownerFactionId) {
        Nex4xAgentManager mgr = Nex4xAgentManager.get();
        if (mgr == null) return;

        AgentTypeConfig cfg = AgentTypeConfigLoader.getConfig(AgentType.DIPLOMAT);
        if (cfg == null || cfg.influenceOwner == null) return;

        for (Nex4xAgentData d : mgr.getAllData()) {
            if (d.getType() != AgentType.DIPLOMAT) continue;
            if (d.isRetraining()) continue;
            // Filter by owner; legacy null-owner agents skipped rather than miscredited.
            if (!ownerFactionId.equals(d.getOwnerFactionId())) {
                if (d.getOwnerFactionId() == null)
                    log.warn("[Nex4x] DiplomatPassiveManager: skipping agent with null owner — sweep may not have run yet");
                continue;
            }
            int level = d.getBuildupTracker().getLevel();
            if (level >= cfg.influenceOwner.length) level = cfg.influenceOwner.length - 1;
            float drip = cfg.influenceOwner[level] * days / 30f;
            if (drip > 0) {
                InfluenceManager.getOrCreate().addLump(ownerFactionId, drip, InfluenceSource.AGENT_ACTION);
            }
        }
    }
}
