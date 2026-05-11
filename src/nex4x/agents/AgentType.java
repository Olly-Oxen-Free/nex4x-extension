package nex4x.agents;

public enum AgentType {
    COVERT("Covert", "Embedment"),
    GUERRILLA("Guerrilla", "Network"),
    DIPLOMAT("Diplomat", "Standing");

    public final String displayName;
    public final String buildupName;

    AgentType(String displayName, String buildupName) {
        this.displayName = displayName;
        this.buildupName = buildupName;
    }
}
