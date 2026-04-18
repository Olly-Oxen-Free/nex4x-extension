package nex4x.coalitions;

import java.io.Serializable;

/** Tension between two coalition members (0-100). */
public class CoalitionTension implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String factionA;
    private final String factionB;
    private float tension;

    public CoalitionTension(String factionA, String factionB) {
        this.factionA = factionA;
        this.factionB = factionB;
        this.tension = 0f;
    }

    public String getFactionA() { return factionA; }
    public String getFactionB() { return factionB; }
    public float getTension() { return tension; }

    public void addTension(float amount) {
        tension = Math.max(0, Math.min(100, tension + amount));
    }

    public void decay(float rate) {
        tension = Math.max(0, tension - rate);
    }

    public boolean involves(String factionId) {
        return factionA.equals(factionId) || factionB.equals(factionId);
    }
}
