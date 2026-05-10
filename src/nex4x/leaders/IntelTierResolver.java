package nex4x.leaders;

import com.fs.starfarer.api.Global;
import nex4x.agents.Nex4xAgentManager;
import org.apache.log4j.Logger;

public class IntelTierResolver {

    private static final Logger log = Global.getLogger(IntelTierResolver.class);

    public static IntelTier resolve(String factionId) {
        float score = 0f;
        try {
            Nex4xAgentManager mgr = Nex4xAgentManager.getOrCreate();
            score = mgr.getDiplomatIntelScore(factionId);
        } catch (Throwable t) {
            log.warn("[Nex4x] IntelTierResolver.resolve(" + factionId + "): " + t.getMessage(), t);
            return IntelTier.NONE;
        }
        if (score >= 0.90f) return IntelTier.FULL;
        if (score >= 0.60f) return IntelTier.GOOD;
        if (score >= 0.25f) return IntelTier.PARTIAL;
        return IntelTier.NONE;
    }
}
