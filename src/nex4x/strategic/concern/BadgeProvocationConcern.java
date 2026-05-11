package nex4x.strategic.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.ai.StrategicAIModule;
import exerelin.campaign.ai.concern.BaseStrategicConcern;
import exerelin.campaign.ai.concern.StrategicConcern;
import nex4x.badges.BadgeType;
import nex4x.badges.FactionBadges;
import nex4x.managers.BadgeManager;
import nex4x.managers.Nex4xManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Concern raised when a specific target faction has accumulated negative badges
 * that provoke us (e.g. WARMONGER, AGGRESSOR, BETRAYER, OATHBREAKER).
 * Priority scales with count × severity.
 */
public class BadgeProvocationConcern extends BaseStrategicConcern {

    private static final List<BadgeType> NEGATIVE = Arrays.asList(
            BadgeType.WARMONGER,
            BadgeType.AGGRESSOR,
            BadgeType.BETRAYER,
            BadgeType.OATHBREAKER);

    private static final float PRIORITY_PER_BADGE = 10f;

    private String provocatorFactionId;

    @Override
    public boolean generate() {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null || ai == null) return false;
        BadgeManager badges = mgr.getBadgeManager();
        if (badges == null) return false;

        Set<String> alreadyWrapped = alreadyWrappedTargets();

        String best = null;
        int bestScore = 0;
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            String fid = f.getId();
            if (fid.equals(ai.getFactionId())) continue;
            if (alreadyWrapped.contains(fid)) continue;
            FactionBadges fb = badges.getBadges(fid);
            int score = 0;
            for (BadgeType t : NEGATIVE) {
                if (fb.hasBadge(t)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = fid;
            }
        }
        if (best == null || bestScore == 0) return false;

        provocatorFactionId = best;
        faction = Global.getSector().getFaction(best);
        return true;
    }

    private Set<String> alreadyWrappedTargets() {
        Set<String> targets = new HashSet<String>();
        collect(ai.getDiploModule(), targets);
        collect(ai.getMilModule(), targets);
        return targets;
    }

    private void collect(StrategicAIModule mod, Set<String> targets) {
        if (mod == null) return;
        for (StrategicConcern c : mod.getCurrentConcerns()) {
            if (c instanceof BadgeProvocationConcern) {
                String t = ((BadgeProvocationConcern) c).provocatorFactionId;
                if (t != null) targets.add(t);
            }
        }
    }

    @Override
    public void update() {
        if (provocatorFactionId == null) return;
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return;
        FactionBadges fb = mgr.getBadgeManager().getBadges(provocatorFactionId);
        int score = 0;
        for (BadgeType t : NEGATIVE) {
            if (fb.hasBadge(t)) score++;
        }
        priority.modifyFlat("nex4x_badges", score * PRIORITY_PER_BADGE,
                "Negative badges on " + provocatorFactionId);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Set getExistingConcernItems() {
        Set s = new HashSet();
        if (provocatorFactionId != null) s.add(provocatorFactionId);
        return s;
    }

    public String getProvocatorFactionId() { return provocatorFactionId; }

    @Override
    public boolean isValid() {
        if (provocatorFactionId == null) return false;
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return false;
        FactionBadges fb = mgr.getBadgeManager().getBadges(provocatorFactionId);
        for (BadgeType t : NEGATIVE) {
            if (fb.hasBadge(t)) return true;
        }
        return false;
    }
}
