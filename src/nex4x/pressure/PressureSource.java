package nex4x.pressure;

/** Categories of bilateral diplomatic pressure. */
public enum PressureSource {
    MILITARY("Military Buildup"),
    ECONOMIC_EXPORT("Export Dependence"),
    ECONOMIC_IMPORT("Import Dependence"),
    GRIEVANCE("Grievance"),
    COALITION("Coalition"),
    CLAIMS("Territorial Claims"),
    DECLARATION("Public Declaration"),
    EVENT("Event"),
    AGENT_ACTION("Agent Action");

    public final String displayName;
    PressureSource(String displayName) { this.displayName = displayName; }
}
