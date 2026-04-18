package nex4x.pressure;

/** Force actions gated by accumulated pressure + influence cost. */
public enum ForceActionType {
    FORCE_NAP("Force NAP", 30f, 20f),
    FORCE_TRADE("Force Trade Agreement", 40f, 25f),
    FORCE_TRIBUTE("Force Tribute", 50f, 35f),
    FORCE_PEACE("Force Peace", 60f, 50f),
    FORCE_RENEWAL("Force Renewal", 45f, 30f),
    FORCE_CONCESSION("Force Concession", 70f, 60f),
    FORCE_VASSALIZATION("Force Vassalization", 100f, 100f);

    public final String displayName;
    public final float pressureThreshold;
    public final float influenceCost;

    ForceActionType(String displayName, float pressureThreshold, float influenceCost) {
        this.displayName = displayName;
        this.pressureThreshold = pressureThreshold;
        this.influenceCost = influenceCost;
    }
}
