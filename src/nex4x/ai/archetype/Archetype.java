package nex4x.ai.archetype;

import nex4x.ai.goals.GoalType;
import nex4x.data.TendencyId;

public enum Archetype {
    TERRITORIAL_EXPANSION("Territorial Expansion", "More land is more power"),
    ECONOMIC_HEGEMONY("Economic Hegemony", "Control the markets"),
    COALITION_BUILDER("Coalition Builder", "Strength in numbers"),
    IDEOLOGICAL_CRUSADE("Ideological Crusade", "The sector must conform"),
    MILITARY_SUPREMACY("Military Supremacy", "Unchallengeable strength"),
    DEFENSIVE_CONSOLIDATION("Defensive Consolidation", "Protect what we have"),
    OPPORTUNIST("Opportunist", "Whatever's best right now");

    public final String displayName;
    public final String description;

    Archetype(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Tendency affinity for initialization seeding.
     * Returns affinity value (0-5) for a given tendency.
     */
    public int getTendencyAffinity(TendencyId tendency) {
        switch (this) {
            case TERRITORIAL_EXPANSION:
                switch (tendency) {
                    case MILITARISTS: return 3;
                    case INDUSTRIALISTS: return 2;
                    case ZEALOTS: return 1;
                    default: return 0;
                }
            case ECONOMIC_HEGEMONY:
                switch (tendency) {
                    case CORPORATISTS: return 5;
                    case INDUSTRIALISTS: return 3;
                    case FEDERALISTS: return 1;
                    default: return 0;
                }
            case COALITION_BUILDER:
                switch (tendency) {
                    case FEDERALISTS: return 5;
                    case CORPORATISTS: return 2;
                    case INDUSTRIALISTS: return 1;
                    case ECOLOGISTS: return 2;
                    default: return 0;
                }
            case IDEOLOGICAL_CRUSADE:
                switch (tendency) {
                    case ZEALOTS: return 5;
                    case MILITARISTS: return 2;
                    default: return 0;
                }
            case MILITARY_SUPREMACY:
                switch (tendency) {
                    case MILITARISTS: return 5;
                    case ZEALOTS: return 2;
                    default: return 0;
                }
            case DEFENSIVE_CONSOLIDATION:
                switch (tendency) {
                    case INDUSTRIALISTS: return 4;
                    case ECOLOGISTS: return 4;
                    case FEDERALISTS: return 3;
                    case MILITARISTS: return 1;
                    default: return 0;
                }
            case OPPORTUNIST:
            default:
                return 0;
        }
    }

    /** Modifier applied to goal priority when this archetype is active. */
    public float getGoalModifier(GoalType goalType) {
        Archetype primary = goalType.getPrimaryArchetype();
        Archetype secondary = goalType.getSecondaryArchetype();

        if (this == primary) return 1.4f;       // 40% boost
        if (this == secondary) return 1.15f;     // 15% boost
        if (opposes(goalType)) return 0.6f;      // 40% penalty
        return 1.0f;
    }

    /** Whether this archetype opposes the given goal type. */
    public boolean opposes(GoalType goalType) {
        switch (this) {
            case TERRITORIAL_EXPANSION:
                return goalType == GoalType.END_WAR || goalType == GoalType.SEEK_PROTECTION;
            case ECONOMIC_HEGEMONY:
                return false;
            case COALITION_BUILDER:
                return goalType == GoalType.EXPLOIT_WEAKNESS;
            case IDEOLOGICAL_CRUSADE:
                return false;
            case MILITARY_SUPREMACY:
                return goalType == GoalType.SEEK_PROTECTION;
            case DEFENSIVE_CONSOLIDATION:
                return goalType == GoalType.CLAIM_TERRITORY
                        || goalType == GoalType.EXPLOIT_WEAKNESS;
            case OPPORTUNIST:
            default:
                return false;
        }
    }
}
