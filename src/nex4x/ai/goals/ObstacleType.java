package nex4x.ai.goals;

public enum ObstacleType {
    MILITARY_DEFENSE("Target has strong fleet/garrison"),
    DEFENSIVE_PACT("Target has defensive pact with 3rd party"),
    ALLIANCE_BLOCK("Target is in coalition"),
    NO_CASUS_BELLI("No valid CB for war goal"),
    INSUFFICIENT_PRESSURE("Need more pressure leverage"),
    RELATION_TOO_LOW("Relations below required threshold"),
    ECONOMIC_COMPETITOR("Rival controls desired market"),
    RESOURCE_SHORTAGE("Insufficient influence/credits/military"),
    ACTIVE_WAR("Currently at war, can't open another front"),
    TREATY_OBLIGATION("Our own agreement prevents this action"),
    NONE("No obstacle — goal achievable now");

    public final String description;

    ObstacleType(String description) {
        this.description = description;
    }
}
