package nex4x.leaders;

import java.util.Map;

public interface DialogueProvider {
    String resolve(LeaderProfile leader,
                   Situation situation,
                   ReputationTier effectiveTier,
                   Map<String, String> context);
}
