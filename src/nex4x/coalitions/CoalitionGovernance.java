package nex4x.coalitions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import nex4x.Nex4xConstants;
import nex4x.agreements.Agreement;
import nex4x.agreements.AgreementManager;
import nex4x.agreements.AgreementType;
import nex4x.integration.NexDiplomacyBridge;
import nex4x.managers.Nex4xManager;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Coalition governance: member management, tension tracking, dissolution. */
public class CoalitionGovernance implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Global.getLogger(CoalitionGovernance.class);

    public static final float TENSION_DECAY_PER_DAY = 0.2f;
    public static final float DISSOLUTION_TENSION_THRESHOLD = 70f;
    /**
     * Minimum raw relationship enforced between coalition members when a new member is
     * admitted. Coalition is the top alliance tier, so this mirrors the friendly floor
     * used by MILITARY_PARTNERSHIP in {@code AgreementManager.relationFloorFor}.
     */
    public static final float COALITION_RELATION_FLOOR = 0.50f;

    private final List<CoalitionTension> tensions = new ArrayList<CoalitionTension>();
    private final List<CoalitionVote> pendingVotes = new ArrayList<CoalitionVote>();

    /** Read-only lookup; returns null if no tension entry exists. Does not mutate state. */
    public CoalitionTension findTension(String factionA, String factionB) {
        for (CoalitionTension t : tensions) {
            if ((t.getFactionA().equals(factionA) && t.getFactionB().equals(factionB))
                    || (t.getFactionA().equals(factionB) && t.getFactionB().equals(factionA))) {
                return t;
            }
        }
        return null;
    }

    /** Returns existing tension entry or creates one. Mutates state. Use only on write paths. */
    public CoalitionTension getOrCreateTension(String factionA, String factionB) {
        CoalitionTension existing = findTension(factionA, factionB);
        if (existing != null) return existing;
        CoalitionTension t = new CoalitionTension(factionA, factionB);
        tensions.add(t);
        return t;
    }

    /** @deprecated use {@link #findTension} (read-only) or {@link #getOrCreateTension} (write). */
    @Deprecated
    public CoalitionTension getTension(String factionA, String factionB) {
        return getOrCreateTension(factionA, factionB);
    }

    public void addTension(String factionA, String factionB, float amount, String reason) {
        getOrCreateTension(factionA, factionB).addTension(amount);
        log.info("[Nex4x] Coalition tension: " + factionA + " / " + factionB + " +" + amount + " (" + reason + ")");
    }

    public float getAverageTension(List<String> memberIds) {
        if (memberIds.size() < 2) return 0f;
        float total = 0f;
        int pairs = 0;
        for (int i = 0; i < memberIds.size(); i++) {
            for (int j = i + 1; j < memberIds.size(); j++) {
                CoalitionTension t = findTension(memberIds.get(i), memberIds.get(j));
                if (t != null) total += t.getTension();
                pairs++;
            }
        }
        return pairs > 0 ? total / pairs : 0f;
    }

    public String getBlocLeader(List<String> memberIds) {
        String leader = null;
        int maxMarkets = 0;
        for (String fid : memberIds) {
            int markets = 0;
            for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                if (fid.equals(m.getFactionId()) && !m.isHidden()) markets++;
            }
            if (markets > maxMarkets) {
                maxMarkets = markets;
                leader = fid;
            }
        }
        return leader;
    }

    public CoalitionVote proposeVote(CoalitionVote.VoteType type, String proposerId, String targetId) {
        CoalitionVote vote = new CoalitionVote(type, proposerId, targetId);
        pendingVotes.add(vote);
        return vote;
    }

    public List<CoalitionVote> getPendingVotes() { return pendingVotes; }
    public List<CoalitionTension> getAllTensions() { return tensions; }

    public void advanceDay() {
        for (CoalitionTension t : tensions) t.decay(TENSION_DECAY_PER_DAY);

        // Resolve pending votes that have timed out
        for (CoalitionVote vote : pendingVotes) {
            if (vote.isResolved()) continue;
            if (!vote.isExpired()) continue;

            // Resolve with real member count from AgreementManager
            List<String> members = getCoalitionMembers(vote.getProposerFactionId());
            int memberCount = members.size();
            String leader = getBlocLeader(members);

            // Members who haven't voted are treated as abstentions (absent from numerator)
            // — we do NOT force-cast their votes; isExpired + resolve handles quorum math.

            boolean decided = vote.resolve(leader, memberCount);
            if (decided && vote.isPassed()) {
                log.info("[Nex4x] Vote PASSED: " + vote.getType()
                        + " in coalition of " + memberCount + " members");
                applyVoteEffect(vote, members);
            } else if (!decided) {
                log.info("[Nex4x] Vote FAILED_QUORUM: " + vote.getType()
                        + " in coalition of " + memberCount);
            } else {
                log.info("[Nex4x] Vote FAILED (majority against): " + vote.getType()
                        + " in coalition of " + memberCount);
            }
        }

        // Prune resolved votes
        Iterator<CoalitionVote> it = pendingVotes.iterator();
        while (it.hasNext()) if (it.next().isResolved()) it.remove();
    }

    /**
     * Apply the in-world effect of a passed vote.
     *
     * <p>A coalition has no standalone data structure: its membership is the transitive
     * closure of active {@link AgreementType#COALITION} pairwise agreements held by
     * {@link AgreementManager}, mirrored into a Nex {@link Alliance} via
     * {@link NexDiplomacyBridge#syncCoalitionToAlliance}. All five vote types therefore act
     * through those two backings (agreements + shadow alliance) plus vanilla diplomacy events.
     */
    private void applyVoteEffect(CoalitionVote vote, List<String> members) {
        try {
            switch (vote.getType()) {
                case DECLARE_WAR: {
                    // Declare war on behalf of all coalition members vs the target faction
                    String targetId = vote.getTargetFactionId();
                    for (String memberId : members) {
                        FactionAPI member = Global.getSector().getFaction(memberId);
                        FactionAPI target = Global.getSector().getFaction(targetId);
                        if (member == null || target == null) continue;
                        if (member.isHostileTo(target)) continue;
                        try {
                            exerelin.campaign.DiplomacyManager.createDiplomacyEvent(
                                    member, target, "declare_war", null);
                            log.info("[Nex4x] Coalition DECLARE_WAR: " + memberId
                                    + " -> " + targetId);
                        } catch (Throwable t) {
                            log.warn("[Nex4x] Coalition war declaration failed for "
                                    + memberId + ": " + t.getMessage());
                        }
                    }
                    break;
                }
                case MAKE_PEACE: {
                    FactionAPI proposer = Global.getSector().getFaction(vote.getProposerFactionId());
                    FactionAPI target = Global.getSector().getFaction(vote.getTargetFactionId());
                    if (proposer != null && target != null) {
                        nex4x.integration.NexDiplomacyBridge.firePeaceTreaty(proposer, target);
                        log.info("[Nex4x] Coalition MAKE_PEACE: " + vote.getProposerFactionId()
                                + " <-> " + vote.getTargetFactionId());
                    }
                    break;
                }
                case ADD_MEMBER: {
                    // Ratify the newcomer into the coalition. The pairwise COALITION agreement
                    // that made them reachable was already created at proposal time
                    // (AgreementManager.createAgreement); the passed vote confirms it by
                    // (a) folding the member into the shadow Nex alliance and
                    // (b) enforcing a friendly relation floor with every existing member.
                    // We deliberately do NOT call createAgreement here: it re-proposes an
                    // ADD_MEMBER vote whenever members.size() > 2, which would loop forever.
                    String newMember = vote.getTargetFactionId();
                    List<String> full = new ArrayList<String>(members);
                    if (newMember != null && !full.contains(newMember)) full.add(newMember);

                    if (newMember != null) {
                        for (String existing : members) {
                            if (existing.equals(newMember)) continue;
                            try {
                                NexDiplomacyBridge.enforceNonAggression(
                                        existing, newMember, COALITION_RELATION_FLOOR);
                            } catch (Throwable t) {
                                log.warn("[Nex4x] ADD_MEMBER relation floor " + existing
                                        + " <-> " + newMember + ": " + t.getMessage());
                            }
                        }
                    }
                    try {
                        NexDiplomacyBridge.syncCoalitionToAlliance(buildCoalitionId(full), full);
                    } catch (Throwable t) {
                        log.warn("[Nex4x] ADD_MEMBER alliance sync failed: " + t.getMessage(), t);
                    }
                    log.info("[Nex4x] Coalition vote ADD_MEMBER applied: " + newMember
                            + " admitted to coalition of " + full.size() + " members");
                    break;
                }
                case KICK_MEMBER: {
                    // Expel the target: cancel every active COALITION agreement it holds, which
                    // (via AgreementManager.maybeSyncCoalitionLeave) also drops it from the shadow
                    // Nex alliance once it has no remaining coalition ties. Each cancellation is
                    // attributed to the remaining party so the expelled faction gains a
                    // TREATY_VIOLATION casus belli (the grievance of being kicked).
                    String kicked = vote.getTargetFactionId();
                    AgreementManager am = getAgreementManager();
                    int cancelled = 0;
                    if (am != null && kicked != null) {
                        for (Agreement a : am.getAgreementsOfType(kicked, AgreementType.COALITION)) {
                            String canceller = a.getOtherFaction(kicked);
                            if (canceller == null) canceller = vote.getProposerFactionId();
                            try {
                                am.cancelWithConsequences(a, canceller);
                                cancelled++;
                            } catch (Throwable t) {
                                log.warn("[Nex4x] KICK_MEMBER cancel failed for " + kicked
                                        + ": " + t.getMessage());
                            }
                        }
                    }
                    // Belt-and-suspenders: mirror DemandManager's explicit alliance leave in case
                    // the agreement teardown above left a stale alliance membership.
                    leaveAllianceQuietly(kicked);
                    log.info("[Nex4x] Coalition vote KICK_MEMBER applied: " + kicked
                            + " expelled (" + cancelled + " coalition agreement(s) cancelled)");
                    break;
                }
                case DISSOLVE: {
                    // Tear the whole coalition down: capture the shadow alliance, cancel every
                    // COALITION agreement among the members (plain cancel — dissolution is mutual,
                    // so no faction is blamed with a CB), then dissolve the Nex alliance outright
                    // and purge intra-coalition tension records.
                    AgreementManager am = getAgreementManager();
                    Alliance alliance = null;
                    for (String fid : members) {
                        try {
                            Alliance a = AllianceManager.getFactionAlliance(fid);
                            if (a != null) { alliance = a; break; }
                        } catch (Throwable t) {
                            log.warn("[Nex4x] DISSOLVE alliance lookup for " + fid
                                    + ": " + t.getMessage());
                        }
                    }

                    int cancelled = 0;
                    if (am != null) {
                        List<Agreement> toCancel = new ArrayList<Agreement>();
                        for (String fid : members) {
                            for (Agreement a : am.getAgreementsOfType(fid, AgreementType.COALITION)) {
                                if (!toCancel.contains(a)) toCancel.add(a);
                            }
                        }
                        for (Agreement a : toCancel) {
                            try {
                                a.cancel();
                                cancelled++;
                            } catch (Throwable t) {
                                log.warn("[Nex4x] DISSOLVE agreement cancel failed: " + t.getMessage());
                            }
                        }
                    }

                    if (alliance != null) {
                        try {
                            AllianceManager.getManager().dissolveAlliance(alliance);
                            log.info("[Nex4x] DISSOLVE: dissolved Nex alliance " + alliance.getName());
                        } catch (Throwable t) {
                            // Fallback: no direct dissolve — have every member leave individually.
                            log.warn("[Nex4x] DISSOLVE: dissolveAlliance failed (" + t.getMessage()
                                    + ") — leaving per member");
                            for (String fid : members) leaveAllianceQuietly(fid);
                        }
                    }

                    clearTensionsAmong(members);
                    log.info("[Nex4x] Coalition vote DISSOLVE applied: coalition of "
                            + members.size() + " members disbanded (" + cancelled
                            + " agreement(s) cancelled)");
                    break;
                }
                default:
                    log.warn("[Nex4x] applyVoteEffect: unhandled vote type " + vote.getType());
                    break;
            }
        } catch (Throwable t) {
            log.warn("[Nex4x] applyVoteEffect failed: " + t.getMessage(), t);
        }
    }

    /**
     * Get all coalition members reachable from the given faction.
     * Delegates to AgreementManager's transitive member lookup.
     */
    private List<String> getCoalitionMembers(String anyMemberId) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr != null) {
            try {
                return mgr.getAgreementManager().getCoalitionMembersFor(anyMemberId);
            } catch (Throwable t) {
                log.warn("[Nex4x] getCoalitionMembers failed: " + t.getMessage());
            }
        }
        // Fallback: just return the single member (quorum will fail for >2 coalitions)
        List<String> fallback = new ArrayList<String>();
        fallback.add(anyMemberId);
        return fallback;
    }

    /** Resolve the shared AgreementManager, or null if the Nex4x manager is unavailable. */
    private AgreementManager getAgreementManager() {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr != null) {
            try {
                return mgr.getAgreementManager();
            } catch (Throwable t) {
                log.warn("[Nex4x] getAgreementManager failed: " + t.getMessage());
            }
        }
        return null;
    }

    /** Remove {@code factionId} from its Nex alliance if it belongs to one. Never throws. */
    private void leaveAllianceQuietly(String factionId) {
        if (factionId == null) return;
        try {
            Alliance alliance = AllianceManager.getFactionAlliance(factionId);
            if (alliance != null) {
                AllianceManager.getManager().leaveAlliance(factionId, alliance);
                log.info("[Nex4x] Coalition leave: " + factionId
                        + " left Nex alliance " + alliance.getName());
            }
        } catch (Throwable t) {
            log.warn("[Nex4x] leaveAllianceQuietly for " + factionId + ": " + t.getMessage(), t);
        }
    }

    /** Drop every tracked tension entry that sits between two of the given members. */
    private void clearTensionsAmong(List<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return;
        Iterator<CoalitionTension> it = tensions.iterator();
        while (it.hasNext()) {
            CoalitionTension t = it.next();
            if (memberIds.contains(t.getFactionA()) && memberIds.contains(t.getFactionB())) {
                it.remove();
            }
        }
    }

    /** Stable coalition id derived from a sorted member list (logging only). */
    private static String buildCoalitionId(List<String> members) {
        List<String> sorted = new ArrayList<String>(members);
        java.util.Collections.sort(sorted);
        StringBuilder sb = new StringBuilder("coalition");
        for (String m : sorted) sb.append('_').append(m);
        return sb.toString();
    }

    public boolean shouldDissolve(List<String> memberIds) {
        return getAverageTension(memberIds) >= DISSOLUTION_TENSION_THRESHOLD;
    }

    public static CoalitionGovernance get() {
        Object raw = Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_COALITION_GOV);
        if (raw instanceof CoalitionGovernance) return (CoalitionGovernance) raw;
        return null;
    }

    public static CoalitionGovernance getOrCreate() {
        CoalitionGovernance mgr = get();
        if (mgr == null) {
            mgr = new CoalitionGovernance();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_COALITION_GOV, mgr);
        }
        return mgr;
    }
}
