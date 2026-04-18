package nex4x.leaders;

public enum Personality {
    HAUGHTY,
    PRAGMATIC,
    PARANOID,
    ZEALOUS,
    MERCANTILE,
    RUTHLESS,
    GENIAL,
    CAPRICIOUS;

    public static Personality safeValueOf(String s) {
        if (s == null) return PRAGMATIC;
        try { return Personality.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return PRAGMATIC; }
    }
}
