package nex4x.agents.actions;

/**
 * Stable string ids for nex4x covert action defs registered with Nex's CovertOpsManager.
 * These match {@code CovertOpsManager.actionDefsById} entries created in
 * {@code Nex4xCovertActionRegistry.register()}.
 */
public final class ActionDefIds {
    private ActionDefIds() {}

    public static final String DEEP_COVER              = "nex4x_deep_cover";
    public static final String DIPLOMAT_OFFICIAL       = "nex4x_diplomat_official_support";
    public static final String DIPLOMAT_LEAK           = "nex4x_diplomat_unofficial_leak";
    public static final String GUERRILLA_BUILD_NETWORK = "nex4x_guerrilla_build_network";
    public static final String GUERRILLA_FALSE_FLAG    = "nex4x_guerrilla_false_flag";
    public static final String GUERRILLA_HIRE_MERCS    = "nex4x_guerrilla_hire_mercs";
    public static final String GUERRILLA_INCITE_RAID   = "nex4x_guerrilla_incite_raid";
}
