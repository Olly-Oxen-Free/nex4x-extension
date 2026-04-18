package nex4x.leaders;

import nex4x.agents.Nex4xAgentManager;

public class IntelTierResolver {

    public static IntelTier resolve(String factionId) {
        float score = 0f;
        try {
            Nex4xAgentManager mgr = Nex4xAgentManager.getOrCreate();
            score = mgr.getDiplomatIntelScore(factionId);
        } catch (Throwable t) {
            return IntelTier.NONE;
        }
        if (score >= 0.90f) return IntelTier.FULL;
        if (score >= 0.60f) return IntelTier.GOOD;
        if (score >= 0.25f) return IntelTier.PARTIAL;
        return IntelTier.NONE;
    }
}
