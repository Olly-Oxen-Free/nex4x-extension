package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import nex4x.leaders.LeaderAccessGate;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Persistent CoreUITabListener.  When Intel opens, runs the full injection
 * here (non-script context — reflection is allowed) and hands off a
 * pre-wired IntelDiplomacyTabInjector to the script system for frame
 * monitoring (which needs only public API).
 */
public class CoreUITabInjectorListener implements CoreUITabListener {

    private static final Logger log = Global.getLogger(CoreUITabInjectorListener.class);

    private static final String BTN_NEG_PREFIX = "dip_inj_neg_";
    private static final String BTN_DET_PREFIX = "nex4x_fb_faction:";

    /** Previous overlay attached during the last Intel open; removed on next reopen to prevent stacking. */
    private static UIPanelAPI lastIntelPanel;
    private static CustomPanelAPI lastOverlay;

    @Override
    public void reportAboutToOpenCoreTab(CoreUITabId tab, Object param) {
        if (tab != CoreUITabId.INTEL) return;
        log.info("[Nex4x] CoreUITabInjectorListener: Intel tab opening");

        // Remove any stale injector from a previous Intel open
        Global.getSector().removeTransientScriptsOfClass(IntelDiplomacyTabInjector.class);

        // Ensure coreUIGetCurrentTab MethodHandle is ready (safe here — listener context).
        IntelReflectionUtil.ensureCoreHandles();

        // Attempt injection now — we are in listener (non-script) context so
        // reflection is permitted.
        tryInjectNow();
    }

    // ── Injection (runs in listener context — reflection OK) ──────────────────

    private void tryInjectNow() {
        // reportAboutToOpenCoreTab fires before the tab switch completes, so
        // getCurrentTab() still returns the previous tab here. Always defer to the
        // retry script which polls each frame until the Intel panel is live.
        log.info("[Nex4x] tryInjectNow: scheduling retry script to poll for Factions button");
        scheduleRetryScript();
    }

    /**
     * Called by IntelDiplomacyTabInjector retry path once it has both panel and button.
     * doInject uses only public API — safe from script context.
     */
    static void injectWithPanelAndButton(UIPanelAPI intelPanel, Object factionsBtn) {
        new CoreUITabInjectorListener().doInject(intelPanel, factionsBtn);
    }


    private void doInject(UIPanelAPI intelPanel, Object factionsBtn) {
        IntelReflectionUtil.setTextSafe(factionsBtn, "Diplomacy [3]");

        float panelW = intelPanel.getPosition().getWidth();
        // Full panel height — overlay covers tab bar too so processInput sees tab clicks.
        float panelH = intelPanel.getPosition().getHeight();
        // Content area height (below the 30px tab bar) used for rendering.
        float contentH = panelH - 30f;

        final DiplomacyTabOverlayModel model  = new DiplomacyTabOverlayModel();
        final DiplomacyOverlayPlugin   plugin = new DiplomacyOverlayPlugin();
        plugin.setModel(model);

        final CustomPanelAPI overlay = Global.getSettings().createCustom(panelW, panelH, plugin);

        final float pw = panelW;
        final float ph = contentH;

        Runnable refresh = new Runnable() {
            public void run() {
                java.util.List<Object> kids = IntelReflectionUtil.getChildrenNonCopy(overlay);
                if (kids != null) kids.clear();
                model.setRefresh(this);
                model.render(overlay, pw, ph);
            }
        };

        model.setRefresh(refresh);
        model.render(overlay, pw, ph);
        plugin.setRefreshCallback(refresh);

        // Remove previous overlay (if any) before adding the new one — prevents stacking
        // across repeated Intel open/close cycles. CoreUITabListener has no symmetric close hook.
        if (lastIntelPanel != null && lastOverlay != null) {
            try {
                lastIntelPanel.removeComponent(lastOverlay);
            } catch (Throwable ignore) { /* prior panel may already be disposed */ }
        }
        lastIntelPanel = intelPanel;
        lastOverlay = overlay;

        intelPanel.addComponent(overlay);
        intelPanel.bringComponentToTop(overlay);
        // Position at top-left corner of intel panel — covers tab bar + content.
        overlay.getPosition().inTL(0f, 0f);
        // Nearly transparent — not 0f so processInput is still active for click detection.
        overlay.setOpacity(0.001f);

        IntelDiplomacyTabInjector monitor =
                new IntelDiplomacyTabInjector(factionsBtn, intelPanel, overlay);
        Global.getSector().addTransientScript(monitor);

        log.info("[Nex4x] Diplomacy tab injected into Intel screen");
    }

    private void scheduleRetryScript() {
        Global.getSector().addTransientScript(new IntelDiplomacyTabInjector());
    }

    // ── Faction card list ─────────────────────────────────────────────────────

    private void buildFactionCardList(TooltipMakerAPI list, float listW) {
        final String playerId = Global.getSector().getPlayerFaction().getId();
        list.addSectionHeading("DIPLOMACY", Alignment.MID, 0f);

        List<FactionAPI> factions = getBrowsableFactions(playerId);
        Collections.sort(factions, new Comparator<FactionAPI>() {
            public int compare(FactionAPI a, FactionAPI b) {
                return Float.compare(b.getRelationship(playerId), a.getRelationship(playerId));
            }
        });

        float btnW = (listW - 16f) / 2f - 2f;
        for (FactionAPI f : factions) {
            float rel      = f.getRelationship(playerId);
            String relStr  = String.format("%+d", nex4x.util.Nex4xRelations.toPercentInt(rel));
            Color relColor = relColor(rel);
            Color base     = f.getBaseUIColor();
            Color dark     = f.getDarkUIColor();

            TooltipMakerAPI card = list.beginImageWithText(f.getCrest(), 48f);
            card.addPara(f.getDisplayName(), base, 0f);
            card.addPara(repLabel(rel) + " (" + relStr + " / 100)", relColor, 2f);
            card.addPara(FactionBrowserPanelModel.repBarAscii(nex4x.util.Nex4xRelations.toPercent(rel)), relColor, 1f);
            list.addImageWithText(4f);

            list.addButton("Negotiate", BTN_NEG_PREFIX + f.getId(),
                    base, dark, Alignment.MID, CutStyle.ALL, btnW, 20f, 2f);
            list.addButton("Details ►", BTN_DET_PREFIX + f.getId(),
                    Misc.getGrayColor(), new Color(20, 20, 20, 255),
                    Alignment.MID, CutStyle.ALL, btnW, 20f, 2f);

            list.addSpacer(6f);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<FactionAPI> getBrowsableFactions(String playerId) {
        List<FactionAPI> out = new ArrayList<FactionAPI>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.isNeutralFaction()) continue;
            if (f.getId().equals(playerId)) continue;
            if (f.getId().equals("derelict") || f.getId().equals("nex_derelict")) continue;
            if (!hasMarkets(f.getId())) continue;
            out.add(f);
        }
        return out;
    }

    private boolean hasMarkets(String factionId) {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId())) return true;
        }
        return false;
    }

    private Color relColor(float rawRel) {
        float pct = nex4x.util.Nex4xRelations.toPercent(rawRel);
        if (pct >= 50f)  return Misc.getPositiveHighlightColor();
        if (pct >= 0f)   return Misc.getTextColor();
        if (pct >= -50f) return new Color(220, 180, 100, 255);
        return Misc.getNegativeHighlightColor();
    }

    private String repLabel(float rawRel) {
        float pct = nex4x.util.Nex4xRelations.toPercent(rawRel);
        if (pct >= 80f)  return "Allied";
        if (pct >= 50f)  return "Cooperative";
        if (pct >= 20f)  return "Favorable";
        if (pct >= -20f) return "Neutral";
        if (pct >= -50f) return "Suspicious";
        if (pct >= -70f) return "Hostile";
        return "Vengeful";
    }
}
