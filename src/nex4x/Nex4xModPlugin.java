package nex4x;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import nex4x.ai.DiplomaticExecutor;
import nex4x.ai.StrategicGoalManager;
import nex4x.ai.archetype.GrandStrategyManager;
import nex4x.data.*;
import nex4x.integration.FactionCompatibility;
import nex4x.listeners.Nex4xEventListener;
import nex4x.managers.Nex4xManager;
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

        // Create faction dossier intel items
        createDossierIntelItems();
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

    @SuppressWarnings("unchecked")
    private void createDossierIntelItems() {
        // Don't duplicate on re-load (reflection — ProfileExtender may not be compiled yet)
        try {
            Class<?> profileExtenderClass = Class.forName("nex4x.ui.ProfileExtender");
            if (!Global.getSector().getIntelManager().getIntel(profileExtenderClass).isEmpty()) {
                return;  // already created
            }

            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                String fid = faction.getId();
                if (fid.equals("derelict") || fid.equals("nex_derelict") || fid.equals("neutral")) continue;
                if (faction.isNeutralFaction()) continue;

                try {
                    exerelin.utilities.NexFactionConfig conf = exerelin.utilities.NexConfig.getFactionConfig(fid);
                    if (conf == null) continue;
                    boolean playable = conf.getClass().getField("playableFaction").getBoolean(conf);
                    if (!playable) continue;
                } catch (Exception e) {
                    continue;
                }

                try {
                    IntelInfoPlugin dossier = (IntelInfoPlugin) profileExtenderClass
                            .getConstructor(String.class).newInstance(fid);
                    Global.getSector().getIntelManager().addIntel(dossier, true);
                } catch (Exception e) {
                    // skip this faction
                }
            }
            log.info("[Nex4x] Created faction dossier intel items");
        } catch (ClassNotFoundException e) {
            log.info("[Nex4x] ProfileExtender not available — skipping dossier creation");
        }
    }
}
