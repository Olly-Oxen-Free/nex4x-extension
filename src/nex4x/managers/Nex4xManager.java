package nex4x.managers;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import nex4x.Nex4xConstants;
import nex4x.agreements.AgreementManager;
import nex4x.leaders.LeaderRegistry;
import nex4x.ai.DiplomaticExecutor;
import nex4x.ai.ReactiveHandler;
import nex4x.ai.StrategicGoalManager;
import nex4x.ai.archetype.GrandStrategyManager;
import nex4x.casusbelli.CasusBelliManager;
import nex4x.agents.Nex4xAgentManager;
import nex4x.agents.diplomat.DiplomatPassiveManager;
import nex4x.coalitions.CoalitionGovernance;
import nex4x.contracts.ContractAuctionManager;
import nex4x.data.TendencyProfile;
import nex4x.declarations.DeclarationManager;
import nex4x.demands.DemandManager;
import nex4x.influence.InfluenceManager;
import nex4x.integration.FactionCompatibility;
import nex4x.mediation.MediationManager;
import nex4x.policies.PolicyManager;
import nex4x.politics.DynamicModifierManager;
import nex4x.pressure.PressureManager;
import nex4x.vassals.VassalManager;
import nex4x.wargoals.WarScoreTracker;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Main manager — persisted in save data.
 * Owns MemoryManager, BadgeManager, player doctrine, and v1 AI engine components.
 * Also an EveryFrameScript for periodic decay advancement.
 */
public class Nex4xManager implements EveryFrameScript, Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(Nex4xManager.class);

    private final MemoryManager memoryManager = new MemoryManager();
    private final BadgeManager badgeManager = new BadgeManager();
    private final AgreementManager agreementManager = new AgreementManager();
    private final AIProposalManager proposalManager = new AIProposalManager();
    private final CasusBelliManager casusBelliManager = new CasusBelliManager();
    private final DeclarationManager declarationManager = new DeclarationManager();
    private final WarScoreTracker warScoreTracker = new WarScoreTracker();
    private final ReactiveHandler reactiveHandler = new ReactiveHandler();
    private final LeaderRegistry leaderRegistry = new LeaderRegistry();

    // v1 AI engine components
    private final GrandStrategyManager grandStrategy = new GrandStrategyManager();
    private final Map<String, StrategicGoalManager> goalManagers =
            new HashMap<String, StrategicGoalManager>();
    private final Map<String, DiplomaticExecutor> executors =
            new HashMap<String, DiplomaticExecutor>();

    // Player faction doctrine (null until faction created)
    private TendencyProfile playerDoctrine;
    /** Player-picked diplomacy trait ids from {@link nex4x.ui.DoctrineSetupDialog} (custom faction). */
    private java.util.ArrayList<String> playerDoctrineTraitIds = new java.util.ArrayList<String>();

    // Decay runs daily
    private final IntervalUtil decayInterval = new IntervalUtil(0.95f, 1.05f);

    /** Not persisted — re-learned each session / load. */
    private transient String lastKnownCommissionFactionId;
    private transient int diploBrainSweepCounter = 0;

    public MemoryManager getMemoryManager() { return memoryManager; }
    public BadgeManager getBadgeManager() { return badgeManager; }
    public AgreementManager getAgreementManager() { return agreementManager; }
    public AIProposalManager getProposalManager() { return proposalManager; }
    public CasusBelliManager getCasusBelliManager() { return casusBelliManager; }
    public DeclarationManager getDeclarationManager() { return declarationManager; }
    public WarScoreTracker getWarScoreTracker() { return warScoreTracker; }
    public ReactiveHandler getReactiveHandler() { return reactiveHandler; }
    public LeaderRegistry getLeaderRegistry() { return leaderRegistry; }
    public GrandStrategyManager getGrandStrategy() { return grandStrategy; }

    public StrategicGoalManager getGoalManager(String factionId) {
        StrategicGoalManager mgr = goalManagers.get(factionId);
        if (mgr == null) {
            mgr = new StrategicGoalManager(factionId);
            goalManagers.put(factionId, mgr);
        }
        return mgr;
    }

    public DiplomaticExecutor getExecutor(String factionId) {
        DiplomaticExecutor exec = executors.get(factionId);
        if (exec == null) {
            exec = new DiplomaticExecutor(factionId);
            executors.put(factionId, exec);
        }
        return exec;
    }

    public TendencyProfile getPlayerDoctrine() { return playerDoctrine; }
    public void setPlayerDoctrine(TendencyProfile doctrine) { this.playerDoctrine = doctrine; }

    public java.util.List<String> getPlayerDoctrineTraitIds() {
        return java.util.Collections.unmodifiableList(playerDoctrineTraitIds);
    }

    public void setPlayerDoctrineTraitIds(java.util.List<String> traitIds) {
        playerDoctrineTraitIds.clear();
        if (traitIds != null) playerDoctrineTraitIds.addAll(traitIds);
    }

    // EveryFrameScript
    @Override
    public boolean isDone() { return false; }
    @Override
    public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);
        decayInterval.advance(days);

        if (decayInterval.intervalElapsed()) {
            float elapsed = decayInterval.getElapsed();

            reactiveHandler.processUrgentQueue(this);
            syncDisallowedFactionsAndProfiles();
            syncPlayerCommissionFaction();

            memoryManager.advanceAllDecay(elapsed);
            badgeManager.advanceAllDecay(elapsed);
            agreementManager.advanceDay();
            casusBelliManager.advanceDay();
            declarationManager.advanceDay(elapsed);
            warScoreTracker.advanceDay();
            proposalManager.advanceDay();

            // v2 — influence, pressure, policies, demands, mediation, contracts, vassals, coalitions, modifiers
            try {
                InfluenceManager.getOrCreate().advanceDay(elapsed);
                PressureManager.getOrCreate().advanceDay();
                DynamicModifierManager.getOrCreate().advanceDay();
                PolicyManager.getOrCreate().advanceDay();
                DemandManager.getOrCreate().advanceDay();
                MediationManager.getOrCreate().advanceDay();
                ContractAuctionManager.getOrCreate().advanceDay();
                VassalManager.getOrCreate().advanceDay();
                CoalitionGovernance.getOrCreate().advanceDay();
            } catch (Exception e) {
                log.error("[Nex4x] v2 daily tick failed: " + e.getMessage());
            }

            // v3 — agent companion data + passive diplomat drip
            try {
                // Ensure every live agent has a companion record with a populated owner id
                // BEFORE buildup/passive/cascade run (they skip null-owner agents).
                nex4x.agents.AgentOwnershipSweep.sweepAndWire(Nex4xAgentManager.getOrCreate());
                Nex4xAgentManager.getOrCreate().advanceAll(elapsed, 1);
                // Nex's CovertOpsManager handles agent action selection; nex4x reacts via
                // Nex4xAgentActionReportListener (registered transient in onGameLoad).
                for (FactionAPI f : Global.getSector().getAllFactions()) {
                    if (f.isNeutralFaction()) continue;
                    DiplomatPassiveManager.advanceDay(elapsed, f.getId());
                }
            } catch (Exception e) {
                log.error("[Nex4x] v3 daily tick failed: " + e.getMessage());
            }

            // Periodic DiplomacyBrain sweep (every 10 ticks ~= 10 days) — idempotent.
            diploBrainSweepCounter++;
            if (diploBrainSweepCounter >= 10) {
                diploBrainSweepCounter = 0;
                try {
                    nex4x.integration.NexDiplomacyBridge.sweepDiplomacyBrains();
                } catch (Throwable t) {
                    log.warn("[Nex4x] DiplomacyBrain sweep: " + t.getMessage());
                }
            }

            // v5 — leader registry daily tick
            try {
                java.util.List<LeaderRegistry.LeaderChangeEvent> changes =
                        leaderRegistry.advanceDay(elapsed);
                for (LeaderRegistry.LeaderChangeEvent evt : changes) {
                    try {
                        com.fs.starfarer.api.Global.getSector().getIntelManager().addIntel(
                                new nex4x.ui.LeaderChangeIntel(evt.factionId, evt.newProfile));
                    } catch (Exception e) {
                        log.error("[Nex4x] Failed to emit LeaderChangeIntel: " + e.getMessage());
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("[Nex4x] (v5) Leader change event: " + evt.factionId
                                + " old=" + evt.oldPersonId + " new=" + evt.newPersonId);
                    }
                }
            } catch (Exception e) {
                log.error("[Nex4x] v5 leader registry tick failed: " + e.getMessage());
            }

            // Re-suppress legacy Nex intels — SectorManager may recreate profiles when
            // new factions go live mid-campaign
            try {
                nex4x.Nex4xModPlugin.suppressLegacyNexIntels();
            } catch (Exception e) {
                log.error("[Nex4x] legacy intel suppression failed: " + e.getMessage());
            }

            // AI Decision Engine — daily advance for all active factions
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                if (faction.isNeutralFaction()) continue;
                if (faction.isPlayerFaction()) continue;

                String fid = faction.getId();
                try {
                    StrategicGoalManager goalMgr = getGoalManager(fid);
                    goalMgr.advanceDay(grandStrategy);

                    DiplomaticExecutor executor = getExecutor(fid);
                    executor.advanceDay(goalMgr, grandStrategy);
                } catch (Exception e) {
                    log.error("[Nex4x] AI advance failed for " + fid + ": " + e.getMessage());
                }
            }
        }
    }

    /** Keep Nex diplomacy brain from simulating new factions; ensure nex4x profiles exist. */
    private void syncDisallowedFactionsAndProfiles() {
        try {
            java.util.List<String> disallowed = DiplomacyManager.disallowedFactions;
            for (String fid : SectorManager.getLiveFactionIdsCopy()) {
                if (!disallowed.contains(fid)) {
                    disallowed.add(fid);
                }
                FactionCompatibility.ensureProfile(fid);
            }
        } catch (Exception e) {
            log.warn("[Nex4x] syncDisallowedFactionsAndProfiles: " + e.getMessage());
        }
    }

    private void syncPlayerCommissionFaction() {
        try {
            String cur = Misc.getCommissionFactionId();
            if (cur == null) cur = "";
            if (lastKnownCommissionFactionId == null) {
                lastKnownCommissionFactionId = cur;
                return;
            }
            if (!lastKnownCommissionFactionId.equals(cur)) {
                reactiveHandler.onPlayerCommissionChange(this, lastKnownCommissionFactionId, cur);
                lastKnownCommissionFactionId = cur;
            }
        } catch (Exception e) {
            log.warn("[Nex4x] syncPlayerCommissionFaction: " + e.getMessage());
        }
    }

    // Static access
    public static Nex4xManager getManager() {
        return (Nex4xManager) Global.getSector().getPersistentData().get(Nex4xConstants.PERSIST_KEY_MANAGER);
    }

    public static Nex4xManager getOrCreateManager() {
        Nex4xManager mgr = getManager();
        if (mgr == null) {
            mgr = new Nex4xManager();
            Global.getSector().getPersistentData().put(Nex4xConstants.PERSIST_KEY_MANAGER, mgr);
            Global.getSector().addScript(mgr);
            log.info("[Nex4x] Created and registered Nex4xManager");
            return mgr;
        }

        // Defensive: re-register as EveryFrameScript if the sector dropped it on load.
        // Without this, daily ticks (decay, AI loop, v2 economies) silently stall post-save.
        boolean present = false;
        for (com.fs.starfarer.api.EveryFrameScript s : Global.getSector().getScripts()) {
            if (s == mgr) { present = true; break; }
        }
        if (!present) {
            Global.getSector().addScript(mgr);
            log.info("[Nex4x] Re-registered Nex4xManager as EveryFrameScript after load");
        }
        return mgr;
    }
}
