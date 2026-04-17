package nex4x.agents;

/**
 * Per-type configuration loaded from agent_types.json.
 * Immutable after loading.
 */
public class AgentTypeConfig {
    public final AgentType type;
    public final String buildupName;
    public final String[] buildupLevelNames;  // 4 names: Fresh, Established, etc.
    public final float buildupRatePerDay;
    public final float buildupDecayPerDay;
    public final int retrainBaseDays;
    public final int retrainDaysPerLevel;
    // Diplomat-only fields (null/0 for other types)
    public final int maxPerFactionPerMarket;
    public final float[] influenceOwner;      // per buildup level
    public final float[] influenceHost;
    public final float[] relationDriftPerDay;
    public final float[] tradeIncomeBonus;

    public AgentTypeConfig(AgentType type, String buildupName, String[] buildupLevelNames,
                           float buildupRatePerDay, float buildupDecayPerDay,
                           int retrainBaseDays, int retrainDaysPerLevel,
                           int maxPerFactionPerMarket,
                           float[] influenceOwner, float[] influenceHost,
                           float[] relationDriftPerDay, float[] tradeIncomeBonus) {
        this.type = type;
        this.buildupName = buildupName;
        this.buildupLevelNames = buildupLevelNames;
        this.buildupRatePerDay = buildupRatePerDay;
        this.buildupDecayPerDay = buildupDecayPerDay;
        this.retrainBaseDays = retrainBaseDays;
        this.retrainDaysPerLevel = retrainDaysPerLevel;
        this.maxPerFactionPerMarket = maxPerFactionPerMarket;
        this.influenceOwner = influenceOwner;
        this.influenceHost = influenceHost;
        this.relationDriftPerDay = relationDriftPerDay;
        this.tradeIncomeBonus = tradeIncomeBonus;
    }

    public String getBuildupLevelName(int level) {
        if (level < 0 || level >= buildupLevelNames.length) return "Unknown";
        return buildupLevelNames[level];
    }
}
