package nex4x.coalitions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.Nex4xConstants;
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
     * MUST: DECLARE_WAR and MAKE_PEACE are fully wired.
     * SHOULD: ADD_MEMBER / KICK_MEMBER / DISSOLVE log results but full agreement teardown
     * is deferred (would require circular dep into AgreementManager on cancel path).
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
                case ADD_MEMBER:
                    // SHOULD: call AgreementManager.createAgreement for each existing-member <-> new-member pair
                    // Deferred: avoiding circular dependency on AgreementManager at this abstraction level.
                    // TODO: wire full agreement creation when AgreementManager accessor is available here.
                    log.info("[Nex4x] Coalition vote ADD_MEMBER passed: "
                            + vote.getTargetFactionId()
                            + " (agreement creation deferred — TODO PRD-022 follow-up)");
                    break;
                case KICK_MEMBER:
                    // SHOULD: cancel all coalition agreements involving the kicked member.
                    // Deferred: same circular-dep concern.
                    // TODO: wire AgreementManager.cancelWithConsequences for each coalition pair.
                    log.info("[Nex4x] Coalition vote KICK_MEMBER passed: "
                            + vote.getTargetFactionId()
                            + " (agreement cancellation deferred — TODO PRD-022 follow-up)");
                    break;
                case DISSOLVE:
                    // SHOULD: cancel all coalition agreements among members.
                    // Deferred.
                    log.info("[Nex4x] Coalition vote DISSOLVE passed"
                            + " (full dissolution deferred — TODO PRD-022 follow-up)");
                    break;
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
