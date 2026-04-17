package nex4x.ai.goals;

import nex4x.ai.archetype.Archetype;

public enum GoalType {
    // Expansion
    CLAIM_TERRITORY("Claim Territory", Category.EXPANSION),
    ECONOMIC_DOMINANCE("Economic Dominance", Category.EXPANSION),
    SPREAD_IDEOLOGY("Spread Ideology", Category.EXPANSION),
    // Security
    CONTAIN_RIVAL("Contain Rival", Category.SECURITY),
    DEFEND_TERRITORY("Defend Territory", Category.SECURITY),
    COUNTER_PRESSURE("Counter Pressure", Category.SECURITY),
    // Diplomacy
    BUILD_ALLIANCE("Build Alliance", Category.DIPLOMACY),
    SECURE_AGREEMENT("Secure Agreement", Category.DIPLOMACY),
    IMPROVE_RELATIONS("Improve Relations", Category.DIPLOMACY),
    // Aggression
    PRESS_GRIEVANCE("Press Grievance", Category.AGGRESSION),
    EXPLOIT_WEAKNESS("Exploit Weakness", Category.AGGRESSION),
    // Survival
    SEEK_PROTECTION("Seek Protection", Category.SURVIVAL),
    END_WAR("End War", Category.SURVIVAL),
    // Maintenance
    RENEW_AGREEMENT("Renew Agreement", Category.MAINTENANCE),
    MANAGE_PRESSURE("Manage Pressure", Category.MAINTENANCE);

    public enum Category { EXPANSION, SECURITY, DIPLOMACY, AGGRESSION, SURVIVAL, MAINTENANCE }

    public final String displayName;
    public final Category category;

    GoalType(String displayName, Category category) {
        this.displayName = displayName;
        this.category = category;
    }

    /** Primary archetype this goal feeds (1.0 weight). */
    public Archetype getPrimaryArchetype() {
        switch (this) {
            case CLAIM_TERRITORY: return Archetype.TERRITORIAL_EXPANSION;
            case ECONOMIC_DOMINANCE: return Archetype.ECONOMIC_HEGEMONY;
            case SPREAD_IDEOLOGY: return Archetype.IDEOLOGICAL_CRUSADE;
            case CONTAIN_RIVAL: return Archetype.MILITARY_SUPREMACY;
            case DEFEND_TERRITORY: return Archetype.DEFENSIVE_CONSOLIDATION;
            case COUNTER_PRESSURE: return Archetype.DEFENSIVE_CONSOLIDATION;
            case BUILD_ALLIANCE: return Archetype.COALITION_BUILDER;
            case SECURE_AGREEMENT: return Archetype.COALITION_BUILDER;
            case IMPROVE_RELATIONS: return Archetype.COALITION_BUILDER;
            case EXPLOIT_WEAKNESS: return Archetype.MILITARY_SUPREMACY;
            case SEEK_PROTECTION: return Archetype.DEFENSIVE_CONSOLIDATION;
            case END_WAR: return Archetype.DEFENSIVE_CONSOLIDATION;
            case RENEW_AGREEMENT: return Archetype.COALITION_BUILDER;
            case MANAGE_PRESSURE: return Archetype.ECONOMIC_HEGEMONY;
            case PRESS_GRIEVANCE: return null; // depends on CB type at runtime
            default: return null;
        }
    }

    /** Secondary archetype (0.3 weight), or null. */
    public Archetype getSecondaryArchetype() {
        switch (this) {
            case CLAIM_TERRITORY: return Archetype.MILITARY_SUPREMACY;
            case CONTAIN_RIVAL: return Archetype.DEFENSIVE_CONSOLIDATION;
            case COUNTER_PRESSURE: return Archetype.COALITION_BUILDER;
            case SECURE_AGREEMENT: return Archetype.ECONOMIC_HEGEMONY;
            case EXPLOIT_WEAKNESS: return Archetype.TERRITORIAL_EXPANSION;
            case SEEK_PROTECTION: return Archetype.COALITION_BUILDER;
            case MANAGE_PRESSURE: return Archetype.MILITARY_SUPREMACY;
            default: return null;
        }
    }

    /** Default goal visibility tier. */
    public GoalVisibility getDefaultVisibility() {
        switch (this) {
            case DEFEND_TERRITORY:
            case END_WAR:
            case BUILD_ALLIANCE:
            case SPREAD_IDEOLOGY:
                return GoalVisibility.PUBLIC;
            case CONTAIN_RIVAL:
            case CLAIM_TERRITORY:
            case ECONOMIC_DOMINANCE:
            case COUNTER_PRESSURE:
            case SECURE_AGREEMENT:
            case IMPROVE_RELATIONS:
            case PRESS_GRIEVANCE:
            case RENEW_AGREEMENT:
            case MANAGE_PRESSURE:
                return GoalVisibility.ALLIED;
            case EXPLOIT_WEAKNESS:
            case SEEK_PROTECTION:
                return GoalVisibility.SECRET;
            default:
                return GoalVisibility.ALLIED;
        }
    }

    public enum GoalVisibility { PUBLIC, ALLIED, SECRET }
}
