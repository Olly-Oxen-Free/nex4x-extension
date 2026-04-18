package nex4x;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import nex4x.ai.DiplomaticExecutor;
import nex4x.ai.StrategicGoalManager;
import nex4x.ai.archetype.GrandStrategyManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.utilities.NexConfig;
import nex4x.data.*;
import nex4x.leaders.DialogueSystem;
import nex4x.integration.FactionCompatibility;
import nex4x.listeners.Nex4xEventListener;
import nex4x.managers.Nex4xManager;
import nex4x.ui.ProfileExtender;
import org.apache.log4j.Logger;
import org.json.JSONObject;

public class Nex4xModPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(Nex4xModPlugin.class);

    @Override
    public void onApplicationLoad() throws Exception {
        log.info("[Nex4x] onApplicationLoad — loading data definitions");

        Nex4xSettings.load();
        BeliefRegistry.load();
        FactionBeliefsLoader.load();
        MemoryTypeRegistry.load();
        TendencyProfileLoader.loadProfiles();
        DialogueSystem.load();
        nex4x.leaders.LeaderConfigRegistry.load();

        // Load v1 AI engine configs
        try {
            JSONObject grandStratConfig = Global.getSettings().loadJSON(
                    "data/config/nex4x/grand_strategy.json");
            GrandStrategyManager.loadConfig(grandStratConfig);

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

        // Register Ashlib Politics tab via ListenerManager (reflection — Ashlib optional dep)
        try {
            Object tabListener = Class.forName("nex4x.ui.PoliticsTabListener")
                    .newInstance();
            Global.getSector().getListenerManager().addListener(tabListener);
            log.info("[Nex4x] Registered Politics tab via Ashlib");
        } catch (Exception e) {
            // Sector not available during onApplicationLoad, or Ashlib absent
            log.info("[Nex4x] Deferring Politics tab registration to onGameLoad");
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        log.info("[Nex4x] onGameLoad (newGame=" + newGame + ")");

        // Initialize or retrieve persisted manager
        Nex4xManager.getOrCreateManager();

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

        // Register Ashlib Politics tab if not registered in onApplicationLoad (reflection)
        try {
            Class<?> tabListenerClass = Class.forName("nex4x.ui.PoliticsTabListener");
            if (!Global.getSector().getListenerManager().hasListenerOfClass(tabListenerClass)) {
                Global.getSector().getListenerManager().addListener(tabListenerClass.newInstance());
                log.info("[Nex4x] Registered Politics tab via Ashlib (deferred)");
            }
        } catch (Exception e) {
            log.warn("[Nex4x] Could not register Politics tab: " + e.getMessage());
        }

        // Ensure all factions have nex4x profiles (auto-derive if missing)
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (!faction.isNeutralFaction()) {
                FactionCompatibility.ensureProfile(faction.getId());
            }
        }

        // Strip v0-era ProfileExtender dossiers left in saves (superseded by FactionBrowserIntel)
        removeLegacyDossierIntels();

        // Create unified Faction Browser intel (one per game)
        createFactionBrowserIntel();

        // Suppress replaced Nex intels — FactionBrowserIntel covers their function
        suppressLegacyNexIntels();
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
            Class<?> browserClass = Class.forName("nex4x.ui.FactionBrowserIntel");
            if (!Global.getSector().getIntelManager().getIntel(browserClass).isEmpty()) {
                return;
            }
            IntelInfoPlugin browser = (IntelInfoPlugin) browserClass
                    .getConstructor().newInstance();
            Global.getSector().getIntelManager().addIntel(browser, true);
            log.info("[Nex4x] Created Faction Browser intel");
        } catch (ClassNotFoundException e) {
            log.info("[Nex4x] FactionBrowserIntel not available — skipping");
        } catch (Exception e) {
            log.warn("[Nex4x] Could not create FactionBrowserIntel: " + e.getMessage());
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

        // Player has a custom faction — auto-derive for v0
        // (Interactive DoctrineSetupDialog is v1 — requires CustomUIPanelPlugin for sliders)
        Nex4xManager mgr = Nex4xManager.getOrCreateManager();
        mgr.setPlayerDoctrine(TendencyProfileLoader.getProfile(playerFactionId));
    }

}
