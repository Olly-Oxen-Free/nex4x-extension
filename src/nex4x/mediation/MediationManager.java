package nex4x.mediation;

import com.fs.starfarer.api.Global;
import nex4x.Nex4xConstants;
import nex4x.influence.InfluenceManager;
import nex4x.influence.InfluenceSource;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Third-party ceasefire mediation. Success grants mediator influence + prestige. */
public class MediationManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Global.getLogger(MediationManager.class);

    public static final float DEFAULT_WINDOW_DAYS = 30f;
    public static final float PROGRESS_PER_DAY = 3f;
    public static final float SUCCESS_THRESHOLD = 100f;
    public static final float SUCCESS_REWARD_MULTIPLIER = 2f;

    private final List<MediationSession> sessions = new ArrayList<MediationSession>();

    private static float now() {
        return nex4x.util.Nex4xClock.currentAbsoluteDay();
    }

    public MediationSession propose(String mediatorId, String belligerentA, String belligerentB,
                                    float influenceInvested) {
        if (mediatorId == null || belligerentA == null || belligerentB == null) {
            log.warn("[Nex4x] Mediation propose: null arg rejected");
            return null;
        }
        if (belligerentA.equals(belligerentB)) {
            log.warn("[Nex4x] Mediation propose: self-belligerent rejected");
            return null;
        }
        if (mediatorId.equals(belligerentA) || mediatorId.equals(belligerentB)) {
            log.warn("[Nex4x] Mediation propose: mediator must not be a belligerent");
            return null;
        }
        if (influenceInvested <= 0f) {
            log.warn("[Nex4x] Mediation propose: zero/negative investment rejected");
            return null;
        }
        // Mediation only makes sense between currently hostile factions.
        com.fs.starfarer.api.campaign.FactionAPI fa = Global.getSector().getFaction(belligerentA);
        com.fs.starfarer.api.campaign.FactionAPI fb = Global.getSector().getFaction(belligerentB);
        if (fa == null || fb == null || !fa.isHostileTo(fb)) {
            log.warn("[Nex4x] Mediation propose: belligerents not at war");
            return null;
        }
        InfluenceManager infl = InfluenceManager.getOrCreate();
        if (!infl.getLedger(mediatorId).canAfford(influenceInvested)) {
            log.info("[Nex4x] Mediation proposal failed: " + mediatorId + " insufficient influence");
            return null;
        }
        infl.getLedger(mediatorId).spend(influenceInvested, InfluenceSource.MEDIATION);
        float t = now();
        MediationSession s = new MediationSession(mediatorId, belligerentA, belligerentB,
                t, t + DEFAULT_WINDOW_DAYS, influenceInvested);
        sessions.add(s);
        log.info("[Nex4x] Mediation proposed: " + mediatorId + " mediating " + belligerentA + "<->" + belligerentB);
        return s;
    }

    public void accept(MediationSession s) {
        if (s.getStatus() != MediationSession.Status.PROPOSED) return;
        s.setStatus(MediationSession.Status.ACTIVE);
        log.info("[Nex4x] Mediation active: " + s.getBelligerentA() + "<->" + s.getBelligerentB());
    }

    public List<MediationSession> getActiveFor(String factionId) {
        List<MediationSession> result = new ArrayList<MediationSession>();
        for (MediationSession s : sessions) if (s.involves(factionId)) result.add(s);
        return result;
    }

    public void advanceDay() {
        float t = now();
        Iterator<MediationSession> it = sessions.iterator();
        while (it.hasNext()) {
            MediationSession s = it.next();
            if (s.getStatus() == MediationSession.Status.ACTIVE) {
                s.addProgress(PROGRESS_PER_DAY);
                if (s.getProgress() >= SUCCESS_THRESHOLD) {
                    s.setStatus(MediationSession.Status.SUCCESS);
                    float reward = s.getInfluenceInvested() * SUCCESS_REWARD_MULTIPLIER;
                    InfluenceManager.getOrCreate().getLedger(s.getMediatorId())
                            .addLump(reward, InfluenceSource.MEDIATION);
                    log.info("[Nex4x] Mediation SUCCESS: " + s.getMediatorId()
                            + " brokers " + s.getBelligerentA() + "<->" + s.getBelligerentB()
                            + " (+" + reward + " influence)");
                    com.fs.starfarer.api.campaign.FactionAPI fa = Global.getSector().getFaction(s.getBelligerentA());
                    com.fs.starfarer.api.campaign.FactionAPI fb = Global.getSector().getFaction(s.getBelligerentB());
                    nex4x.integration.NexDiplomacyBridge.firePeaceTreaty(fa, fb);
                    log.info("[Nex4x] Mediation " + s.getMediatorId() + " brokered peace: "
                            + s.getBelligerentA() + " <-> " + s.getBelligerentB());
                }
            }
            if (s.getStatus() == MediationSession.Status.PROPOSED && t >= s.getExpiryDay()) {
                s.setStatus(MediationSession.Status.EXPIRED);
            }
            if ((s.getStatus() == MediationSession.Status.SUCCESS
                    || s.getStatus() == MediationSession.Status.FAILED
                    || s.getStatus() == MediationSession.Status.EXPIRED)
                    && t > s.getExpiryDay() + 60f) {
                it.remove();
            }
        }
    }

    public List<MediationSession> getAll() { return sessions; }

    public static MediationManager get() {
        Object raw = Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_MEDIATION_MANAGER);
        if (raw instanceof MediationManager) return (MediationManager) raw;
        return null;
    }

    public static MediationManager getOrCreate() {
        MediationManager mgr = get();
        if (mgr == null) {
            mgr = new MediationManager();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_MEDIATION_MANAGER, mgr);
        }
        return mgr;
    }
}
