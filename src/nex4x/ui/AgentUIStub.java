package nex4x.ui;

import com.fs.starfarer.api.Global;
import nex4x.agents.AgentType;
import nex4x.agents.Nex4xAgentData;
import nex4x.agents.Nex4xAgentManager;
import org.apache.log4j.Logger;

/**
 * Read-only debug helper that logs nex4x agent companion data alongside Nex's AgentIntel UI.
 * Intentionally minimal — Nex's AgentIntel owns the canonical UI; this just dumps text
 * summaries through the console logger for diagnostic use.
 */
public class AgentUIStub {
    private static final Logger log = Global.getLogger(AgentUIStub.class);

    public static String renderAgentSummary(String agentId) {
        Nex4xAgentManager mgr = Nex4xAgentManager.get();
        if (mgr == null) return "(no agent data)";
        Nex4xAgentData d = mgr.get(agentId);
        if (d == null) return "(unknown agent)";
        StringBuilder sb = new StringBuilder();
        sb.append(d.getType()).append(" | ");
        sb.append("level ").append(d.getBuildupTracker().getLevel());
        sb.append(" (").append((int) d.getBuildupTracker().getPoints()).append(" pts)");
        if (d.isRetraining()) {
            sb.append(" | RETRAINING -> ").append(d.getRetrainTargetType());
            sb.append(" (").append((int) d.getRetrainDaysRemaining()).append("d)");
        }
        return sb.toString();
    }

    public static void logAllAgents() {
        Nex4xAgentManager mgr = Nex4xAgentManager.get();
        if (mgr == null) return;
        for (java.util.Map.Entry<String, Nex4xAgentData> e : mgr.getAll().entrySet()) {
            log.info("[Nex4x] Agent " + e.getKey() + ": " + renderAgentSummary(e.getKey()));
        }
    }
}
