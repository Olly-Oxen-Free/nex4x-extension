package nex4x.influence;

/** Source categories for influence income and expenses. */
public enum InfluenceSource {
    BUILDING_EMBASSY("Diplomatic Embassy"),
    BUILDING_INTEL_BUREAU("Intelligence Bureau"),
    MARKET_SIZE("Market Size"),
    STABILITY("Stability"),
    AI_CORE_ALPHA("Alpha AI Core"),
    AI_CORE_BETA("Beta AI Core"),
    AI_CORE_GAMMA("Gamma AI Core"),
    AGREEMENT_UPKEEP("Agreement Upkeep"),
    POLICY_UPKEEP("Policy Upkeep"),
    FORCE_ACTION("Force Action"),
    DEMAND("Demand"),
    EVENT_BONUS("Event"),
    TREASURY_TRANSFER("Treasury Transfer"),
    AGENT_ACTION("Agent Action"),
    CONTRACT("Contract"),
    MEDIATION("Mediation"),
    CONTRACT_PAYOFF("Contract Payoff");

    public final String displayName;

    InfluenceSource(String displayName) {
        this.displayName = displayName;
    }
}
