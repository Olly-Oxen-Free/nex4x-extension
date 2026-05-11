package nex4x.ai.posture;

public enum DiplomaticPosture {
    HOSTILE("Hostile", 0),
    PRESSURING("Pressuring", -30),
    NEGOTIATING("Negotiating", -999),  // suppresses military
    ALLIED("Allied", -999),            // blocks military entirely
    DEFENSIVE("Defensive", 0);         // only defensive concerns

    public final String displayName;
    /** Priority modifier applied to StrategicAI military concerns. -999 = blocked. */
    public final int militaryConcernModifier;

    DiplomaticPosture(String displayName, int militaryConcernModifier) {
        this.displayName = displayName;
        this.militaryConcernModifier = militaryConcernModifier;
    }

    public boolean blocksMilitaryConcerns() {
        return militaryConcernModifier <= -999;
    }
}
