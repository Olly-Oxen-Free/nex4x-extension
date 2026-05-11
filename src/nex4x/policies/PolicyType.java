package nex4x.policies;

import nex4x.data.TendencyId;

/**
 * All policy types. Each is gated by a dominant tendency + minimum weight,
 * and costs influence per cycle while active.
 */
public enum PolicyType {
    MILITARY_BUILDUP("Military Buildup", TendencyId.MILITARISTS, 2.0f, 3.0f,
            "Fleet maintenance reduced 15%, patrol frequency +25%"),
    AGGRESSIVE_POSTURE("Aggressive Posture", TendencyId.MILITARISTS, 3.0f, 5.0f,
            "War desire +20, pressure accumulation +25%"),
    OPEN_DIPLOMACY("Open Diplomacy", TendencyId.FEDERALISTS, 2.0f, 3.0f,
            "Agreement proposal costs -30%, negotiation fatigue -50%"),
    COLLECTIVE_SECURITY("Collective Security", TendencyId.FEDERALISTS, 3.0f, 5.0f,
            "Defensive pact honor +40, coalition vote weight +20%"),
    FREE_TRADE("Free Trade", TendencyId.CORPORATISTS, 2.0f, 3.0f,
            "Trade income +20%, embargo effectiveness -25%"),
    ECONOMIC_SANCTIONS("Economic Sanctions", TendencyId.CORPORATISTS, 3.0f, 4.0f,
            "Embargo effectiveness +50%, diplomatic pressure from trade +30%"),
    IDEOLOGICAL_PURITY("Ideological Purity", TendencyId.ZEALOTS, 2.0f, 4.0f,
            "Belief-gated CB threshold -25%, ideological enemy relations -10"),
    MISSIONARY_CAMPAIGN("Missionary Campaign", TendencyId.ZEALOTS, 3.5f, 6.0f,
            "Ideology spread rate x2, diplomatic reputation -5 with non-aligned"),
    INFRASTRUCTURE_INVESTMENT("Infrastructure Investment", TendencyId.INDUSTRIALISTS, 2.0f, 3.0f,
            "Market stability +1, industry build time -20%"),
    AUTARKY("Autarky", TendencyId.INDUSTRIALISTS, 3.0f, 5.0f,
            "Import dependency pressure -50%, trade income -30%"),
    CONSERVATION_MANDATE("Conservation Mandate", TendencyId.ECOLOGISTS, 2.0f, 3.0f,
            "Anti-bombardment CB threshold -50%, stability +1 on low-pollution markets");

    public final String displayName;
    public final TendencyId requiredTendency;
    public final float requiredWeight;
    public final float influenceUpkeep;
    public final String description;

    PolicyType(String displayName, TendencyId requiredTendency,
               float requiredWeight, float influenceUpkeep, String description) {
        this.displayName = displayName;
        this.requiredTendency = requiredTendency;
        this.requiredWeight = requiredWeight;
        this.influenceUpkeep = influenceUpkeep;
        this.description = description;
    }
}
