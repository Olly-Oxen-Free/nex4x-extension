package nex4x.leaders;

public enum IntelTier {
    NONE(0.40f),
    PARTIAL(0.20f),
    GOOD(0.10f),
    FULL(0.02f);

    public final float marginFraction;
    IntelTier(float m) { this.marginFraction = m; }
}
