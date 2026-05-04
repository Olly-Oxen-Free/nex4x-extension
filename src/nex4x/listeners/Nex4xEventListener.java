package nex4x.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import nex4x.managers.Nex4xManager;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Listens to campaign events and creates memories + badge progress.
 * Registered as transient in onGameLoad (re-registered each load).
 */
public class Nex4xEventListener extends BaseCampaignEventListener {

    private static final Logger log = Global.getLogger(Nex4xEventListener.class);

    public Nex4xEventListener() {
        super(true);  // transient — re-registered each onGameLoad
    }

    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (primaryWinner == null || !battle.hasSnapshots()) return;

        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return;

        String winnerId = primaryWinner.getFaction().getId();

        // Collect losing faction IDs from the other side snapshot
        List<CampaignFleetAPI> loserFleets = battle.getOtherSideSnapshotFor(primaryWinner);
        Set<String> processedLosers = new HashSet<String>();

        for (CampaignFleetAPI fleet : loserFleets) {
            if (fleet.getFaction() == null) continue;
            String loserId = fleet.getFaction().getId();
            if (loserId.equals(winnerId)) continue;
            if (processedLosers.contains(loserId)) continue;
            processedLosers.add(loserId);

            // Only track "significant" battles (more than a patrol skirmish)
            float loserFP = 0;
            for (com.fs.starfarer.api.fleet.FleetMemberAPI fm : fleet.getFleetData().getMembersListCopy()) {
                loserFP += fm.getFleetPointCost();
            }
            if (loserFP < 30) continue;  // skip trivial engagements

            mgr.getMemoryManager().createMemory("military_victory", winnerId, loserId,
                    "Won battle in " + primaryWinner.getContainingLocation().getName());
            mgr.getMemoryManager().createMemory("military_defeat", loserId, winnerId, null);
        }
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        // Trade-related memory events — placeholder for v1
    }

    @Override
    public void reportPlayerReputationChange(String factionId, float delta) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null || factionId == null) return;
        try {
            mgr.getReactiveHandler().onPlayerReputationChange(mgr, factionId, delta);
        } catch (Exception e) {
            log.warn("[Nex4x] reportPlayerReputationChange: " + e.getMessage());
        }
    }

    @Override
    public void reportPlayerReputationChange(PersonAPI person, float delta) {
        if (person == null || person.getFaction() == null) return;
        reportPlayerReputationChange(person.getFaction().getId(), delta);
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null || result == null || !result.didPlayerWin()) return;
        try {
            com.fs.starfarer.api.campaign.EngagementResultForFleetAPI lost = result.getLoserResult();
            if (lost == null || lost.getFleet() == null || lost.getFleet().getFaction() == null) return;
            if (lost.getFleet().getFaction().isPlayerFaction()) return;
            // Light memory hook — distinguish bounties vs generic combat in a future pass.
            mgr.getReactiveHandler().onPlayerBountyEngagement(mgr, lost.getFleet().getFaction().getId());
        } catch (Exception e) {
            log.warn("[Nex4x] reportPlayerEngagement: " + e.getMessage());
        }
    }
}
