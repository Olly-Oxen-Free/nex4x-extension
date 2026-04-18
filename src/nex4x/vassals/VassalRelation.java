package nex4x.vassals;

import java.io.Serializable;

/** Overlord-vassal pair with tier + independence tracking. */
public class VassalRelation implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String overlordId;
    private final String vassalId;
    private VassalTier tier;
    private final float createdDay;
    private float independenceDesire;
    private boolean rebellionActive;

    public VassalRelation(String overlordId, String vassalId, VassalTier tier, float createdDay) {
        this.overlordId = overlordId;
        this.vassalId = vassalId;
        this.tier = tier;
        this.createdDay = createdDay;
        this.independenceDesire = 0f;
        this.rebellionActive = false;
    }

    public String getOverlordId() { return overlordId; }
    public String getVassalId() { return vassalId; }
    public VassalTier getTier() { return tier; }
    public float getCreatedDay() { return createdDay; }
    public float getIndependenceDesire() { return independenceDesire; }
    public boolean isRebellionActive() { return rebellionActive; }

    public void setTier(VassalTier tier) { this.tier = tier; }
    public void setRebellionActive(boolean active) { this.rebellionActive = active; }

    public void shiftDesire(float amount) {
        independenceDesire = Math.max(0, Math.min(100, independenceDesire + amount));
    }

    public boolean advanceDay(float overlordWeakness, float vassalMilitarism) {
        float dailyGrowth = (1f - tier.autonomy) * 0.1f;
        dailyGrowth += overlordWeakness * 0.05f;
        dailyGrowth += vassalMilitarism * 0.02f;
        if (overlordWeakness < 0.2f && tier == VassalTier.TRIBUTARY) dailyGrowth -= 0.05f;
        shiftDesire(dailyGrowth);
        return independenceDesire >= 80f && !rebellionActive;
    }
}
