package nex4x.strategic.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.ai.StrategicAIModule;
import exerelin.campaign.ai.concern.BaseStrategicConcern;
import exerelin.campaign.ai.concern.StrategicConcern;
import nex4x.data.BeliefDef;
import nex4x.data.BeliefRegistry;
import nex4x.data.FactionBeliefs;
import nex4x.data.FactionBeliefsLoader;

import java.util.HashSet;
import java.util.Set;

/**
 * Concern raised when a target faction's beliefs conflict with ours on shared
 * categories — severity scales with magnitude of misalignment on shared topics.
 * Public-tier beliefs only; secret beliefs don't raise this concern.
 */
public class BeliefAlignmentConcern extends BaseStrategicConcern {

    private static final float PRIORITY_PER_CLASH = 6f;

    private String targetFactionId;
    private int lastClashScore;

    @Override
    public boolean generate() {
        if (ai == null) return false;
        String myId = ai.getFactionId();
        FactionBeliefs mine = FactionBeliefsLoader.getBeliefs(myId);
        if (mine == null || mine.getEntries().isEmpty()) return false;

        Set<String> wrapped = alreadyWrappedTargets();

        String best = null;
        int bestScore = 0;
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            String fid = f.getId();
            if (fid.equals(myId)) continue;
            if (wrapped.contains(fid)) continue;
            FactionBeliefs theirs = FactionBeliefsLoader.getBeliefs(fid);
            if (theirs == null) continue;
            int score = clashScore(mine, theirs);
            if (score > bestScore) {
                bestScore = score;
                best = fid;
            }
        }
        if (best == null) return false;

        targetFactionId = best;
        lastClashScore = bestScore;
        faction = Global.getSector().getFaction(best);
        return true;
    }

    private int clashScore(FactionBeliefs a, FactionBeliefs b) {
        int score = 0;
        for (FactionBeliefs.BeliefEntry ae : a.getEntries()) {
            if (ae.visibility != FactionBeliefs.Visibility.PUBLIC) continue;
            BeliefDef adef = BeliefRegistry.get(ae.beliefId);
            if (adef == null) continue;
            for (FactionBeliefs.BeliefEntry be : b.getEntries()) {
                if (be.visibility != FactionBeliefs.Visibility.PUBLIC) continue;
                BeliefDef bdef = BeliefRegistry.get(be.beliefId);
                if (bdef == null) continue;
                if (adef.category == bdef.category && !adef.id.equals(bdef.id)) {
                    score += ae.strength * be.strength;
                }
            }
        }
        return score;
    }

    private Set<String> alreadyWrappedTargets() {
        Set<String> targets = new HashSet<String>();
        collect(ai.getDiploModule(), targets);
        return targets;
    }

    private void collect(StrategicAIModule mod, Set<String> targets) {
        if (mod == null) return;
        for (StrategicConcern c : mod.getCurrentConcerns()) {
            if (c instanceof BeliefAlignmentConcern) {
                String t = ((BeliefAlignmentConcern) c).targetFactionId;
                if (t != null) targets.add(t);
            }
        }
    }

    @Override
    public void update() {
        if (targetFactionId == null) return;
        priority.modifyFlat("nex4x_beliefs", lastClashScore * PRIORITY_PER_CLASH,
                "Belief clash with " + targetFactionId);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Set getExistingConcernItems() {
        Set s = new HashSet();
        if (targetFactionId != null) s.add(targetFactionId);
        return s;
    }

    public String getTargetFactionId() { return targetFactionId; }

    @Override
    public boolean isValid() {
        return targetFactionId != null && lastClashScore > 0;
    }
}
