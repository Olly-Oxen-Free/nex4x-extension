package nex4x.leaders;

import java.util.Map;

public class StaticPoolDialogueProvider implements DialogueProvider {
    private final DialogueSelector selector;

    public StaticPoolDialogueProvider(DialogueSelector selector) {
        this.selector = selector;
    }

    public String resolve(LeaderProfile leader, Situation situation,
                          ReputationTier effectiveTier, Map<String, String> context) {
        return selector.select(leader, situation, effectiveTier, context);
    }
}
