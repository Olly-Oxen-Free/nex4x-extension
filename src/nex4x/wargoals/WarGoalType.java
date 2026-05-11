package nex4x.wargoals;

/**
 * Six war goal types. Selected when declaring war; determines peace terms available.
 * See master spec §2.5.
 */
public enum WarGoalType {
    TERRITORIAL_CLAIM("Territorial Claim",
            "Capture specific markets or systems from the target faction.",
            true),
    HUMILIATION("Humiliation",
            "Force the target to surrender and accept a period of weakness.",
            false),
    REGIME_CHANGE("Regime Change",
            "Destroy the target faction's leadership structure.",
            false),
    CONTAINMENT("Containment",
            "Reduce the target faction's military and economic power.",
            false),
    SUBJUGATION("Subjugation",
            "Force the target into a vassal relationship.",
            false),
    TOTAL_WAR("Total War",
            "Annihilate the target faction completely.",
            false);

    public final String displayName;
    public final String description;
    /** Whether this goal requires a specific market/system target. */
    public final boolean requiresTarget;

    WarGoalType(String displayName, String description, boolean requiresTarget) {
        this.displayName = displayName;
        this.description = description;
        this.requiresTarget = requiresTarget;
    }
}
