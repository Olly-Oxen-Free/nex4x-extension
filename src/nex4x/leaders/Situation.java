package nex4x.leaders;

public enum Situation {
    GREETING,
    NEGOTIATION_ACCEPT,
    NEGOTIATION_REJECT,
    NEGOTIATION_COUNTER,
    DEMAND_HEARD,
    DEMAND_REFUSED,
    CONCESSION_MADE,
    ALLIANCE_PROPOSED,
    ALLIANCE_ACCEPTED,
    ALLIANCE_REJECTED,
    NAP_PROPOSED,
    NAP_ACCEPTED,
    FRIENDSHIP_DECLARED,
    DENOUNCEMENT_HEARD,
    DENOUNCEMENT_MADE,
    TRADE_PACT_PROPOSED,
    TRADE_PACT_REFUSED,
    TRIBUTE_OFFERED,
    TRIBUTE_DEMANDED,
    WAR_DECLARED_BY_AI,
    WAR_DECLARED_BY_PLAYER,
    PEACE_PROPOSED_BY_AI,
    PEACE_ACCEPTED,
    PEACE_REJECTED,
    INSULTED,
    PRAISED,
    BADGE_EARNED_POSITIVE,
    BADGE_EARNED_NEGATIVE,
    CAPITAL_VISIT_FIRST,
    CAPITAL_VISIT_REPEAT,
    FAREWELL;

    public String jsonKey() { return name().toLowerCase(); }

    public static Situation fromJsonKey(String s) {
        if (s == null) return null;
        try { return Situation.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
