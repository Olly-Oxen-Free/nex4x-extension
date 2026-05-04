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
import nex4x.ui.FactionBrowserIntel;
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

        // Initialize or retrieve persisted manager
        Nex4xManager.getOrCreateManager();

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

        // Strip v0-era ProfileExtender dossiers left in saves (superseded by FactionBrowserIntel)
        removeLegacyDossierIntels();

        createFactionBrowserIntel();

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
            if (!Global.getSector().getListenerManager().hasListenerOfClass(Nex4xInvasionBridge.class)) {
                Global.getSector().getListenerManager().addListener(new Nex4xInvasionBridge());
            }
            if (!Global.getSector().getListenerManager().hasListenerOfClass(Nex4xRaidBridge.class)) {
                Global.getSector().getListenerManager().addListener(new Nex4xRaidBridge());
            }
            if (!Global.getSector().getListenerManager().hasListenerOfClass(Nex4xAgentActionReportListener.class)) {
                Global.getSector().getListenerManager().addListener(new Nex4xAgentActionReportListener());
            }
        } catch (Exception e) {
            Global.getLogger(Nex4xModPlugin.class).warn("[Nex4x] registerNexCampaignBridges: " + e.getMessage());
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

    private void createFactionBrowserIntel() {
        try {
            if (!Global.getSector().getIntelManager().getIntel(FactionBrowserIntel.class).isEmpty()) {
                return;
            }
            FactionBrowserIntel browser = new FactionBrowserIntel();
            Global.getSector().getIntelManager().addIntel(browser, true);
            log.info("[Nex4x] Created Faction Browser intel");
        } catch (Throwable t) {
            log.error("[Nex4x] Could not create FactionBrowserIntel: " + t.getMessage(), t);
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
                if (wait > 8f) {
                    Nex4xManager mgr = Nex4xManager.getOrCreateManager();
                    mgr.setPlayerDoctrine(TendencyProfileLoader.getProfile("player"));
                    done = true;
                }
            }
        });
    }

}
