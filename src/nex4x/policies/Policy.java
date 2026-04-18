package nex4x.policies;

import java.io.Serializable;

/** Active policy instance for a specific faction. */
public class Policy implements Serializable {
    private static final long serialVersionUID = 1L;

    private final PolicyType type;
    private final float adoptedDay;
    private boolean active;

    public Policy(PolicyType type, float adoptedDay) {
        this.type = type;
        this.adoptedDay = adoptedDay;
        this.active = true;
    }

    public PolicyType getType() { return type; }
    public float getAdoptedDay() { return adoptedDay; }
    public boolean isActive() { return active; }
    public void revoke() { this.active = false; }
    public float getUpkeep() { return type.influenceUpkeep; }
}
