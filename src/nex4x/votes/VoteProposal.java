package nex4x.votes;

import nex4x.agreements.AgreementType;
import nex4x.ai.goals.GoalType;

import java.io.Serializable;

/**
 * A proposal being put to an advisory vote.
 * Used when the player is commissioned in a faction.
 * See master spec §5.2.8.
 */
public class VoteProposal implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum ProposalType {
        DECLARE_WAR("Declare War"),
        MAKE_PEACE("Make Peace"),
        PROPOSE_AGREEMENT("Propose Agreement"),
        CANCEL_AGREEMENT("Cancel Agreement"),
        DECLARE_FRIENDSHIP("Declare Friendship"),
        DENOUNCE("Denounce"),
        ADOPT_POLICY("Adopt Policy"),
        REVOKE_POLICY("Revoke Policy");

        public final String displayName;
        ProposalType(String displayName) { this.displayName = displayName; }
    }

    private final ProposalType type;
    private final String proposingFactionId;
    private final String targetFactionId;        // nullable for policy proposals
    private final AgreementType agreementType;   // nullable, for agreement proposals
    private final GoalType relatedGoal;          // nullable, for context
    private final String description;

    private VoteOutcome outcome;
    private boolean resolved;

    public VoteProposal(ProposalType type, String proposingFactionId,
                        String targetFactionId, AgreementType agreementType,
                        GoalType relatedGoal, String description) {
        this.type = type;
        this.proposingFactionId = proposingFactionId;
        this.targetFactionId = targetFactionId;
        this.agreementType = agreementType;
        this.relatedGoal = relatedGoal;
        this.description = description;
        this.resolved = false;
    }

    public ProposalType getType() { return type; }
    public String getProposingFactionId() { return proposingFactionId; }
    public String getTargetFactionId() { return targetFactionId; }
    public AgreementType getAgreementType() { return agreementType; }
    public GoalType getRelatedGoal() { return relatedGoal; }
    public String getDescription() { return description; }
    public VoteOutcome getOutcome() { return outcome; }
    public boolean isResolved() { return resolved; }

    public void resolve(VoteOutcome outcome) {
        this.outcome = outcome;
        this.resolved = true;
    }
}
