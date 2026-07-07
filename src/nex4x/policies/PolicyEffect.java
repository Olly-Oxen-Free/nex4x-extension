package nex4x.policies;

/** Stat-modifier lookups for active policies. */
public class PolicyEffect {
    public static float getModifier(PolicyType type, String stat) {
        switch (type) {
            case MILITARY_BUILDUP:
                if ("fleet_maintenance".equals(stat)) return -0.15f;
                if ("patrol_frequency".equals(stat)) return 0.25f;
                break;
            case AGGRESSIVE_POSTURE:
                if ("war_desire".equals(stat)) return 20f;
                if ("pressure_accumulation".equals(stat)) return 0.25f;
                break;
            case OPEN_DIPLOMACY:
                if ("proposal_cost".equals(stat)) return -0.30f;
                if ("negotiation_fatigue".equals(stat)) return -0.50f;
                break;
            case COLLECTIVE_SECURITY:
                if ("pact_honor".equals(stat)) return 40f;
                if ("coalition_vote_weight".equals(stat)) return 0.20f;
                break;
            case FREE_TRADE:
                if ("trade_income".equals(stat)) return 0.20f;
                if ("embargo_effectiveness".equals(stat)) return -0.25f;
                break;
            case ECONOMIC_SANCTIONS:
                if ("embargo_effectiveness".equals(stat)) return 0.50f;
                if ("trade_pressure".equals(stat)) return 0.30f;
                break;
            case IDEOLOGICAL_PURITY:
                if ("belief_cb_threshold".equals(stat)) return -0.25f;
                if ("ideological_enemy_relations".equals(stat)) return -10f;
                break;
            case INFRASTRUCTURE_INVESTMENT:
                if ("market_stability".equals(stat)) return 1f;
                if ("build_time".equals(stat)) return -0.20f;
                break;
            case AUTARKY:
                if ("import_pressure".equals(stat)) return -0.50f;
                if ("trade_income".equals(stat)) return -0.30f;
                break;
            case MISSIONARY_CAMPAIGN:
                if ("ideology_spread_rate".equals(stat)) return 0.40f;
                if ("ideological_enemy_relations".equals(stat)) return -15f;
                if ("belief_cb_threshold".equals(stat)) return -0.20f;
                break;
            case CONSERVATION_MANDATE:
                if ("market_stability".equals(stat)) return 1f;
                if ("trade_income".equals(stat)) return -0.10f;
                if ("ecological_pressure_threshold".equals(stat)) return 0.30f;
                break;
            default: break;
        }
        return 0f;
    }
}
