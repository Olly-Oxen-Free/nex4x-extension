package nex4x;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import nex4x.ai.DiplomaticExecutor;
import nex4x.ai.StrategicGoalManager;
import nex4x.ai.archetype.GrandStrategyManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.utilities.NexConfig;
import nex4x.data.*;
import nex4x.leaders.DialogueSystem;
import nex4x.integration.FactionCompatibility;
import nex4x.agents.Nex4xAgentActionReportListener;
import nex4x.listeners.Nex4xEventListener;
import nex4x.listeners.Nex4xInvasionBridge;
import nex4x.listeners.Nex4xRaidBridge;
import nex4x.managers.Nex4xManager;
import nex4x.ui.AgreementManagerIntel;
import nex4x.ui.DoctrineSetupDialog;
import nex4x.ui.IntelReflectionUtil;
import nex4x.ui.ProfileExtender;
import org.apache.log4j.Logger;
import org.json.JSONObject;

public class Nex4xModPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(Nex4xModPlugin.class);

    @Override
    public void onApplicationLoad() throws Exception {
        log.info("[Nex4x] onApplicationLoad — loading data definitions");

        Nex4xSettings.load();
        nex4x.leaders.LeaderAccessConfig.load();
        BeliefRegistry.load();
        FactionBeliefsLoader.load();
        MemoryTypeRegistry.load();
        TendencyProfileLoader.loadProfiles();
        DialogueSystem.load();
        nex4x.leaders.LeaderConfigRegistry.load();
        nex4x.declarations.DeclarationConfig.load();
        nex4x.negotiation.BaseValueTable.load();

        // Load v1 AI engine configs
        try {
            JSONObject grandStratConfig = Global.getSettings().loadJSON(
                    "data/config/nex4x/grand_strategy.json");
            GrandStrategyManager.loadConfig(grandStratConfig);
            nex4x.ai.archetype.ArchetypeOverrideRegistry.load();

            JSONObject goalConfig = Global.getSettings().loadJSON(
                    "data/config/nex4x/goal_weights.json");
            StrategicGoalManager.loadConfig(goalConfig);

            JSONObject execConfig = Global.getSettings().loadJSON(
                    "data/config/nex4x/diplomatic_executor.json");
            DiplomaticExecutor.loadConfig(execConfig);

            log.info("[Nex4x] AI engine configs loaded");
        } catch (Exception e) {
            log.error("[Nex4x] Failed to load AI engine configs: " + e.getMessage(), e);
        }

        // Politics tab removed: anchoring on "Fleet" NPEs Ashlib CommandTabTracker when other mods
        // rewrite the command bar. Factions tab registers in onGameLoad instead.

        // Plug nex4x concerns/actions into Nex's StrategicAI def manager.
        try {
            nex4x.integration.Nex4xStrategicAIConcerns.register();
        } catch (Throwable t) {
            log.warn("[Nex4x] Strategic AI concern registration: " + t.getMessage(), t);
        }

        // Register nex4x covert action defs with Nex CovertOpsManager.
        try {
            nex4x.agents.actions.Nex4xCovertActionRegistry.register();
        } catch (Throwable t) {
            log.warn("[Nex4x] Covert action registration: " + t.getMessage(), t);
        }
    }

    @Override
    public void onNewGame() {
        log.info("[Nex4x] onNewGame");
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        log.info("[Nex4x] onNewGameAfterEconomyLoad — seeding manager early so procgen can reach it");
        try {
            Nex4xManager.getOrCreateManager();
            FactionBeliefsLoader.loadForLiveFactions();
        } catch (Throwable t) {
            log.warn("[Nex4x] onNewGameAfterEconomyLoad seed failed: " + t.getMessage(), t);
        }
    }

    @Override
    public void beforeGameSave() {
        log.info("[Nex4x] beforeGameSave");
        try {
            nex4x.util.FactionPowerRankings.invalidate();
        } catch (Throwable t) {
            log.warn("[Nex4x] beforeGameSave: " + t.getMessage(), t);
        }
    }

    @Override
    public void afterGameSave() {
        log.info("[Nex4x] afterGameSave");
    }

    @Override
    public void onGameSaveFailed() {
        log.warn("[Nex4x] onGameSaveFailed — save failed; check XStream errors above");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        log.info("[Nex4x] onGameLoad (newGame=" + newGame + ")");

        try {
            Global.getSector().getListenerManager().removeListenerOfClass(
                    nex4x.ui.PoliticsTabListener.class);
        } catch (Throwable t) {
            log.warn("[Nex4x] Legacy Politics tab listener sweep: " + t.getMessage());
        }
        try {
            Global.getSector().getListenerManager().removeListenerOfClass(
                    nex4x.ui.FactionsTabListener.class);
        } catch (Throwable t) {
            log.warn("[Nex4x] FactionsTabListener sweep: " + t.getMessage());
        }

        // Initialize or retrieve persisted manager
        Nex4xManager.getOrCreateManager();

        // Load belief JSONs for any modded factions present in this save.
        try {
            FactionBeliefsLoader.loadForLiveFactions();
        } catch (Throwable t) {
            log.warn("[Nex4x] FactionBeliefsLoader.loadForLiveFactions: " + t.getMessage(), t);
        }

        // Backfill Nex Alliance shadows for in-flight COALITION agreements (PRD-009 9g).
        try {
            nex4x.managers.Nex4xManager mgr = nex4x.managers.Nex4xManager.getManager();
            if (mgr != null) {
                nex4x.agreements.AgreementManager am = mgr.getAgreementManager();
                int backfilled = 0;
                for (nex4x.agreements.Agreement a : am.getAllAgreements()) {
                    if (!a.isActive()) continue;
                    if (a.getType() != nex4x.agreements.AgreementType.COALITION) continue;
                    if (nex4x.integration.NexDiplomacyBridge.ensureAlliance(
                            a.getFactionIdA(), a.getFactionIdB()) != null) {
                        backfilled++;
                    }
                }
                if (backfilled > 0) {
                    log.info("[Nex4x] Backfilled " + backfilled + " coalition alliances on load");
                }
            }
        } catch (Throwable t) {
            log.warn("[Nex4x] COALITION backfill: " + t.getMessage(), t);
        }

        applyNex4xAuthorityOverNexDiplomacy();

        // Register campaign event listener (transient — re-registered each load)
        boolean listenerExists = false;
        for (CampaignEventListener listener : Global.getSector().getAllListeners()) {
            if (listener instanceof Nex4xEventListener) {
                listenerExists = true;
                break;
            }
        }
        if (!listenerExists) {
            Global.getSector().addTransientListener(new Nex4xEventListener());
            log.info("[Nex4x] Registered event listener");
        }

        registerNexCampaignBridges();

        // Ensure all factions have nex4x profiles (auto-derive if missing)
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (!faction.isNeutralFaction()) {
                FactionCompatibility.ensureProfile(faction.getId());
            }
        }

        // Strip v0-era ProfileExtender dossiers left in saves
        removeLegacyDossierIntels();

        sweepNullImmigrationModifiers();

        IntelReflectionUtil.init();

        registerCommandTabs();

        registerIntelTabInjector();

        // Ensure the player's agreements dashboard is always reachable
        createAgreementManagerIntel();

        // Suppress replaced Nex intels — FactionBrowserIntel covers their function
        suppressLegacyNexIntels();
    }

    /**
     * Nex4x owns diplomacy AI: disable Nex StrategicAI and block Nex DiplomacyManager brains
     * for all live factions.
     */
    private static void applyNex4xAuthorityOverNexDiplomacy() {
        try {
            NexConfig.enableStrategicAI = false;
            NexConfig.showStrategicAI = false;
            StrategicAI.removeAIs();
        } catch (Exception e) {
            Global.getLogger(Nex4xModPlugin.class).warn("[Nex4x] StrategicAI shutdown: " + e.getMessage());
        }
        try {
            java.util.List<String> disallowed = DiplomacyManager.disallowedFactions;
            for (String fid : exerelin.campaign.SectorManager.getLiveFactionIdsCopy()) {
                if (!disallowed.contains(fid)) {
                    disallowed.add(fid);
                }
            }
        } catch (Exception e) {
            Global.getLogger(Nex4xModPlugin.class).warn("[Nex4x] disallowedFactions seed: " + e.getMessage());
        }
    }

    private static void registerNexCampaignBridges() {
        try {
            // Purge any persistent entries from legacy saves (pre-fix, registered without transient flag).
            com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI lm =
                    Global.getSector().getListenerManager();
            if (lm.hasListenerOfClass(Nex4xInvasionBridge.class)) {
                lm.removeListenerOfClass(Nex4xInvasionBridge.class);
                log.info("[Nex4x] Purged stale persistent Nex4xInvasionBridge");
            }
            if (lm.hasListenerOfClass(Nex4xRaidBridge.class)) {
                lm.removeListenerOfClass(Nex4xRaidBridge.class);
                log.info("[Nex4x] Purged stale persistent Nex4xRaidBridge");
            }
            if (lm.hasListenerOfClass(Nex4xAgentActionReportListener.class)) {
                lm.removeListenerOfClass(Nex4xAgentActionReportListener.class);
                log.info("[Nex4x] Purged stale persistent Nex4xAgentActionReportListener");
            }
            // Register transient (true = not saved with sector). Re-runs each onGameLoad.
            lm.addListener(new Nex4xInvasionBridge(), true);
            lm.addListener(new Nex4xRaidBridge(), true);
            lm.addListener(new Nex4xAgentActionReportListener(), true);
            // Sanity assert
            if (!lm.hasListenerOfClass(Nex4xInvasionBridge.class)
                    || !lm.hasListenerOfClass(Nex4xRaidBridge.class)
                    || !lm.hasListenerOfClass(Nex4xAgentActionReportListener.class)) {
                log.warn("[Nex4x] registerNexCampaignBridges: post-register assert failed");
            }
        } catch (Exception e) {
            Global.getLogger(Nex4xModPlugin.class).warn("[Nex4x] registerNexCampaignBridges: " + e.getMessage(), e);
        }
    }

    private void removeLegacyDossierIntels() {
        try {
            java.util.List<IntelInfoPlugin> stale =
                    Global.getSector().getIntelManager().getIntel(ProfileExtender.class);
            for (IntelInfoPlugin intel : stale) {
                Global.getSector().getIntelManager().removeIntel(intel);
            }
            if (!stale.isEmpty()) {
                log.info("[Nex4x] Removed " + stale.size() + " legacy ProfileExtender dossier(s)");
            }
        } catch (Exception e) {
            log.warn("[Nex4x] Failed to sweep legacy dossiers: " + e.getMessage());
        }
    }

    private void sweepNullImmigrationModifiers() {
        try {
            int removed = 0;
            for (com.fs.starfarer.api.campaign.econ.MarketAPI m :
                    Global.getSector().getEconomy().getMarketsCopy()) {
                removed += sweepNullsFromModifierSet(m.getImmigrationModifiers());
                removed += sweepNullsFromModifierSet(m.getTransientImmigrationModifiers());
            }
            if (removed > 0) {
                log.warn("[Nex4x] Removed " + removed + " null immigration modifier(s) from markets");
            }
        } catch (Exception e) {
            log.warn("[Nex4x] sweepNullImmigrationModifiers: " + e.getMessage());
        }
    }

    private static int sweepNullsFromModifierSet(
            java.util.LinkedHashSet<com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier> set) {
        if (set == null) return 0;
        int count = 0;
        java.util.Iterator<com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier> it = set.iterator();
        while (it.hasNext()) {
            if (it.next() == null) { it.remove(); count++; }
        }
        return count;
    }

    public static void suppressLegacyNexIntels() {
        if (Nex4xSettings.showLegacyNexIntels) return;

        // Global toggle — hides Nex StrategicAI intel for all non-player factions
        NexConfig.showStrategicAI = false;

        // Remove existing DiplomacyProfileIntels for all non-neutral factions
        DiplomacyManager dm = DiplomacyManager.getManager();
        if (dm != null) {
            for (FactionAPI f : Global.getSector().getAllFactions()) {
                if (f.isNeutralFaction()) continue;
                try {
                    dm.removeDiplomacyProfile(f.getId());
                } catch (Exception e) {
                    // profile not present — ignore
                }
            }
        }
    }

    private void registerIntelTabInjector() {
        try {
            if (!Global.getSector().getListenerManager().hasListenerOfClass(
                    nex4x.ui.CoreUITabInjectorListener.class)) {
                Global.getSector().getListenerManager().addListener(
                        new nex4x.ui.CoreUITabInjectorListener(), true);
                log.info("[Nex4x] Registered CoreUITabInjectorListener");
            }
        } catch (Exception e) {
            log.error("[Nex4x] registerIntelTabInjector: " + e.getMessage(), e);
        }
    }

    private void registerCommandTabs() {
        try {
            if (!Global.getSector().getListenerManager().hasListenerOfClass(nex4x.ui.DiplomacyTabListener.class)) {
                Global.getSector().getListenerManager().addListener(new nex4x.ui.DiplomacyTabListener(), true);
                log.info("[Nex4x] Registered DiplomacyTabListener");
            }
        } catch (Exception e) {
            log.error("[Nex4x] registerCommandTabs: " + e.getMessage(), e);
        }
    }

    private void createAgreementManagerIntel() {
        try {
            if (!Global.getSector().getIntelManager().getIntel(AgreementManagerIntel.class).isEmpty()) {
                return;
            }
            AgreementManagerIntel intel = new AgreementManagerIntel();
            Global.getSector().getIntelManager().addIntel(intel, true);
            log.info("[Nex4x] Created Agreement Manager intel");
        } catch (Throwable t) {
            log.error("[Nex4x] Could not create AgreementManagerIntel: " + t.getMessage(), t);
        }
    }

    @Override
    public void onNewGameAfterTimePass() {
        // If player chose an existing faction, auto-derive doctrine from that faction's traits
        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        if (!playerFactionId.equals("player")) {
            Nex4xManager mgr = Nex4xManager.getOrCreateManager();
            mgr.setPlayerDoctrine(TendencyProfileLoader.getProfile(playerFactionId));
            return;
        }

        // Custom player faction — open doctrine dialog once a campaign UI dialog exists.
        // Fallback timeout below is real game-time (runWhilePaused=false), so it does not
        // fire while the player is reading the opening bar dialog. Extended from 8s to 60s
        // to give the player ample time to reach an interaction dialog naturally.
        Global.getSector().addTransientScript(new EveryFrameScript() {
            private float wait;
            private boolean done;

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public boolean runWhilePaused() {
                return false;
            }

            @Override
            public void advance(float amount) {
                if (done) return;
                wait += amount;
                if (wait < 0.2f) return;
                InteractionDialogAPI d = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
                if (d != null) {
                    d.showCustomDialog(720, 600, new DoctrineSetupDialog());
                    done = true;
                    return;
                }
                if (wait > 60f) {
                    log.warn("[Nex4x] Doctrine dialog never opened (60s game-time) — applying default profile");
                    Nex4xManager mgr = Nex4xManager.getOrCreateManager();
                    mgr.setPlayerDoctrine(TendencyProfileLoader.getProfile("player"));
                    done = true;
                }
            }
        });
    }

}
