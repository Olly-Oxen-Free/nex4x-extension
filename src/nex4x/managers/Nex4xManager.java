package nex4x.managers;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import nex4x.Nex4xConstants;
import nex4x.agreements.AgreementManager;
import nex4x.ai.DiplomaticExecutor;
import nex4x.ai.ReactiveHandler;
import nex4x.ai.StrategicGoalManager;
import nex4x.ai.archetype.GrandStrategyManager;
import nex4x.casusbelli.CasusBelliManager;
import nex4x.data.TendencyProfile;
import nex4x.declarations.DeclarationManager;
import nex4x.integration.FactionCompatibility;
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

    // v1 AI engine components
    private final GrandStrategyManager grandStrategy = new GrandStrategyManager();
    private final Map<String, StrategicGoalManager> goalManagers =
            new HashMap<String, StrategicGoalManager>();
    private final Map<String, DiplomaticExecutor> executors =
            new HashMap<String, DiplomaticExecutor>();

    // Player faction doctrine (null until faction created)
    private TendencyProfile playerDoctrine;

    // Decay runs daily
    private final IntervalUtil decayInterval = new IntervalUtil(0.95f, 1.05f);

    public MemoryManager getMemoryManager() { return memoryManager; }
    public BadgeManager getBadgeManager() { return badgeManager; }
    public AgreementManager getAgreementManager() { return agreementManager; }
    public AIProposalManager getProposalManager() { return proposalManager; }
    public CasusBelliManager getCasusBelliManager() { return casusBelliManager; }
    public DeclarationManager getDeclarationManager() { return declarationManager; }
    public WarScoreTracker getWarScoreTracker() { return warScoreTracker; }
    public ReactiveHandler getReactiveHandler() { return reactiveHandler; }
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
            memoryManager.advanceAllDecay(elapsed);
            badgeManager.advanceAllDecay(elapsed);
            agreementManager.advanceDay();
            casusBelliManager.advanceDay();
            declarationManager.advanceDay();
            warScoreTracker.advanceDay();
            proposalManager.advanceDay();

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
        }
        return mgr;
    }
}
