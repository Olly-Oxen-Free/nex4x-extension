package nex4x.agents.security;

import java.io.Serializable;

/** Per-market counter-agent security profile. */
public class MarketSecurityData implements Serializable {
    private static final long serialVersionUID = 1L;

    private float baseDetection;
    private float cyberBonus;
    private float embassyAssistBonus;
    private float patrolAssistBonus;

    public MarketSecurityData() {}

    public float getBaseDetection() { return baseDetection; }
    public void setBaseDetection(float v) { baseDetection = v; }

    public float getCyberBonus() { return cyberBonus; }
    public void setCyberBonus(float v) { cyberBonus = v; }

    public float getEmbassyAssistBonus() { return embassyAssistBonus; }
    public void setEmbassyAssistBonus(float v) { embassyAssistBonus = v; }

    public float getPatrolAssistBonus() { return patrolAssistBonus; }
    public void setPatrolAssistBonus(float v) { patrolAssistBonus = v; }

    public float getTotal() {
        return baseDetection + cyberBonus + embassyAssistBonus + patrolAssistBonus;
    }
}
