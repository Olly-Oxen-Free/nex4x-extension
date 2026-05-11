package nex4x.mediation;

import java.io.Serializable;

/** One mediator brokering ceasefire between two belligerents. */
public class MediationSession implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status { PROPOSED, ACTIVE, SUCCESS, FAILED, EXPIRED }

    private final String mediatorId;
    private final String belligerentA;
    private final String belligerentB;
    private final float startDay;
    private final float expiryDay;
    private final float influenceInvested;
    private Status status = Status.PROPOSED;
    private float progress;

    public MediationSession(String mediatorId, String belligerentA, String belligerentB,
                            float startDay, float expiryDay, float influenceInvested) {
        this.mediatorId = mediatorId;
        this.belligerentA = belligerentA;
        this.belligerentB = belligerentB;
        this.startDay = startDay;
        this.expiryDay = expiryDay;
        this.influenceInvested = influenceInvested;
    }

    public String getMediatorId() { return mediatorId; }
    public String getBelligerentA() { return belligerentA; }
    public String getBelligerentB() { return belligerentB; }
    public float getStartDay() { return startDay; }
    public float getExpiryDay() { return expiryDay; }
    public float getInfluenceInvested() { return influenceInvested; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public float getProgress() { return progress; }
    public void addProgress(float amount) { progress = Math.max(0, Math.min(100, progress + amount)); }
    public boolean involves(String f) {
        return mediatorId.equals(f) || belligerentA.equals(f) || belligerentB.equals(f);
    }
}
