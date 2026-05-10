package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import nex4x.agreements.AgreementManager;
import nex4x.ai.archetype.Archetype;
import nex4x.managers.Nex4xManager;
import nex4x.pressure.PressureManager;
import nex4x.util.FactionPowerRankings;
import rolflectionlib.util.RolfLectionUtil; // used by getDiploEvents

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model for the Diplomacy overlay injected into the Intel → Factions sub-tab.
 * Three-column layout: faction list | main detail | sidebar (crest/allies/enemies).
 *
 * FactionAPI.getRelationship() returns -1.0..1.0.
 */
public class DiplomacyTabOverlayModel implements Serializable {
    private static final long serialVersionUID = 1L;

    // ── Sort ─────────────────────────────────────────────────────────────────

    public enum SortMode {
        ALLIANCE("Alliance"), POWER("Power Rank"), RELATION("Relation");
        public final String label;
        SortMode(String l) { this.label = l; }
        public SortMode next() { SortMode[] v = values(); return v[(ordinal()+1) % v.length]; }
    }

    // ── Button IDs ────────────────────────────────────────────────────────────

    private static final String BTN_FACTION   = "dtom_fac:";
    private static final String BTN_SORT      = "dtom_sort";
    private static final String BTN_DIR       = "dtom_dir";
    private static final String BTN_NEGOTIATE = "dtom_neg";
    private static final String BTN_ALLY_NAV  = "dtom_ally:";

    // ── State ─────────────────────────────────────────────────────────────────

    private String   selectedFactionId;
    private SortMode sortMode      = SortMode.ALLIANCE;
    private boolean  sortAscending = true;

    /** Set by CoreUITabInjectorListener before each render so FactionCardPlugin can trigger refresh. */
    transient Runnable refresh;

    public void setRefresh(Runnable r) { this.refresh = r; }

    // ── Layout constants ──────────────────────────────────────────────────────

    private static final float LIST_W  = 270f;
    private static final float SIDE_W  = 200f;
    private static final float GAP     = 6f;
    // Card fill colors — black unselected, light grey selected
    private static final Color CARD_BG_NORMAL   = new Color(0,   0,   0,   255);
    private static final Color CARD_BG_SELECTED = new Color(45,  45,  50,  255);

    // ── Public entry point ────────────────────────────────────────────────────

    public void render(CustomPanelAPI panel, float w, float h) {
        log.debug("[Nex4x] render() start w=" + w + " h=" + h);
        try {
        FactionPowerRankings.rebuild();

        float detailW = Math.max(200f, w - LIST_W - GAP);
        float mainW   = detailW - SIDE_W - GAP;

        // List column: CustomPanelAPI with manual Y tracking
        CustomPanelAPI listPanel = Global.getSettings().createCustom(LIST_W, h, new ListPlugin());
        buildFactionListManual(listPanel, LIST_W, h);
        panel.addComponent(listPanel);
        listPanel.getPosition().inTL(0, 0);

        TooltipMakerAPI main = panel.createUIElement(mainW, h, true);
        buildDetailMain(main, mainW);
        panel.addUIElement(main).inTL(LIST_W + GAP, 0);

        TooltipMakerAPI side = panel.createUIElement(SIDE_W, h, true);
        buildDetailSidebar(side, SIDE_W);
        panel.addUIElement(side).inTL(LIST_W + GAP + mainW + GAP, 0);
        log.info("[Nex4x] render() complete");
        } catch (Throwable t) {
            log.error("[Nex4x] render() threw: " + t, t);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LEFT PANEL — manual Y layout (avoids scrollable-tooltip negative-spacer issues)
    // ─────────────────────────────────────────────────────────────────────────

    private static final float CARD_H  = 54f;
    private static final float LABEL_H = 16f;

    private void buildFactionListManual(CustomPanelAPI lp, float w, float h) {
        Color playerColor = Global.getSector().getPlayerFaction().getBaseUIColor();
        Color playerDark  = Global.getSector().getPlayerFaction().getDarkUIColor();
        String playerId   = Global.getSector().getPlayerFaction().getId();
        float btnW = w - 4f;
        float y = 4f;

        // Sort button
        y = addListButton(lp, "Sort: " + sortMode.label, BTN_SORT, playerColor, playerDark, btnW, y);
        // Dir button
        y = addListButton(lp, sortAscending ? "Order: Ascending" : "Order: Descending",
                BTN_DIR, playerColor, playerDark, btnW, y + 2f);
        y += 4f;

        List<FactionAPI> factions = getBrowsableFactions();

        if (sortMode == SortMode.ALLIANCE) {
            y = buildAllianceGroupsManual(lp, factions, playerId, btnW, y);
        } else {
            sortFactions(factions, playerId);
            for (FactionAPI f : factions) {
                y = addFactionCardManual(lp, f, playerId, btnW, y);
                y += 2f;
            }
        }
    }

    /** Adds a single full-width button to lp at y, returns new y. */
    private float addListButton(CustomPanelAPI lp, String label, Object id,
                                Color base, Color dark, float w, float y) {
        TooltipMakerAPI el = lp.createUIElement(w, 22f, false);
        el.addButton(label, id, base, dark, Alignment.MID, CutStyle.TL_BR, w, 22f, 0f);
        lp.addUIElement(el).inTL(2f, y);
        return y + 22f;
    }

    /** Adds a gray section label to lp at y, returns new y. */
    private float addListLabel(CustomPanelAPI lp, String text, float w, float y) {
        TooltipMakerAPI el = lp.createUIElement(w, LABEL_H, false);
        el.addPara(text, Misc.getGrayColor(), 0f);
        lp.addUIElement(el).inTL(2f, y);
        return y + LABEL_H;
    }

    private float buildAllianceGroupsManual(CustomPanelAPI lp, List<FactionAPI> factions,
                                            String playerId, float w, float y) {
        Map<String, List<FactionAPI>> groups  = new LinkedHashMap<String, List<FactionAPI>>();
        List<FactionAPI>              unallied = new ArrayList<FactionAPI>();
        for (FactionAPI f : factions) {
            Alliance a = AllianceManager.getFactionAlliance(f.getId());
            if (a == null) { unallied.add(f); continue; }
            String name = a.getName();
            if (!groups.containsKey(name)) groups.put(name, new ArrayList<FactionAPI>());
            groups.get(name).add(f);
        }
        List<String> groupNames = new ArrayList<String>(groups.keySet());
        if (!sortAscending) Collections.reverse(groupNames);
        for (String groupName : groupNames) {
            y = addListLabel(lp, "-- " + groupName + " --", w, y + 4f);
            List<FactionAPI> members = groups.get(groupName);
            sortFactionsByRelation(members, playerId);
            for (FactionAPI f : members) { y = addFactionCardManual(lp, f, playerId, w, y); y += 2f; }
        }
        if (!unallied.isEmpty()) {
            y = addListLabel(lp, "-- Independent --", w, y + 4f);
            sortFactionsByRelation(unallied, playerId);
            for (FactionAPI f : unallied) { y = addFactionCardManual(lp, f, playerId, w, y); y += 2f; }
        }
        return y;
    }

    private float addFactionCardManual(CustomPanelAPI lp, FactionAPI f,
                                       String playerId, float w, float y) {
        boolean sel  = f.getId().equals(selectedFactionId);
        Color base   = f.getBaseUIColor();
        // Semi-transparent faction color bg: 40 alpha normal, 90 alpha selected
        Color fill   = new Color(base.getRed(), base.getGreen(), base.getBlue(), sel ? 90 : 40);
        float cardH = 54f;
        float imgH  = 44f;
        float imgW  = imgH * 4f / 3f; // ~58.67 — 4:3 rectangle
        float imgPad = (cardH - imgH) / 2f;

        // Layer 1: full-card button (border + tinted bg, receives clicks)
        TooltipMakerAPI btnEl = lp.createUIElement(w, cardH, false);
        btnEl.addButton(" ", BTN_FACTION + f.getId(),
                base, fill, Alignment.MID, CutStyle.NONE, w, cardH, 0f);
        lp.addUIElement(btnEl).inTL(0f, y);

        // Layer 2: 4:3 crest image, vertically centered
        TooltipMakerAPI imgEl = lp.createUIElement(imgW, imgH, false);
        imgEl.addImage(f.getCrest(), imgW, imgH, 0f);
        lp.addUIElement(imgEl).inTL(2f, y + imgPad);

        // Layer 3: name + relation bar to the right of the image
        float textX = imgW + 4f;
        float textW = w - textX - 2f;
        TooltipMakerAPI textEl = lp.createUIElement(textW, cardH, false);
        textEl.addPara(f.getDisplayName(), base, 4f);
        textEl.addRelationshipBar(f, 2f);
        lp.addUIElement(textEl).inTL(textX, y);

        return y + cardH;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CENTRE — main detail content
    // ─────────────────────────────────────────────────────────────────────────

    private void buildDetailMain(TooltipMakerAPI info, float w) {
        if (selectedFactionId == null) {
            info.addPara("Select a faction to view diplomacy details.", Misc.getGrayColor(), 20f);
            return;
        }
        FactionAPI f = Global.getSector().getFaction(selectedFactionId);
        if (f == null) { info.addPara("Faction unavailable.", Misc.getGrayColor(), 10f); return; }

        String playerId = Global.getSector().getPlayerFaction().getId();
        float  rel      = f.getRelationship(playerId);
        Color  fBase    = f.getBaseUIColor();

        // ── Large title — Orbitron Very Large font, no band ──
        info.setTitleOrbitronVeryLarge();
        info.addTitle(f.getDisplayName(), fBase);

        // ── Archetype pill badge (below title, before attitude) ───────────────
        String archetype = getArchetype(f.getId());
        if (!archetype.isEmpty()) {
            float pillW = Math.min(w - 8f, archetype.length() * 9f + 24f);
            info.addButton(archetype, "dtom_badge_noop",
                    fBase, f.getDarkUIColor(), Alignment.MID, CutStyle.ALL, pillW, 18f, 4f);
        }

        // ── Attitude box ──────────────────────────────────────────────────────
        info.addSectionHeading("Attitude", Alignment.MID, 6f);
        String relStr = String.format("%.0f / 100", rel * 100f);
        LabelAPI attLbl = info.addPara("Attitude:  " + relLabel(rel) + " (" + relStr + ")", 6f);
        attLbl.setHighlight(relLabel(rel) + " (" + relStr + ")");
        attLbl.setHighlightColor(relColor(rel));
        info.addRelationshipBar(f, 4f);

        // ── Lore description ──────────────────────────────────────────────────
        info.addSectionHeading("About", Alignment.MID, 6f);
        addLoreDescription(info, f, w);

        // ── Illegal commodities — horizontal icon grid ────────────────────────
        List<String> illegal = getIllegalCommodities(f);
        if (!illegal.isEmpty()) {
            info.addSectionHeading("Illegal Commodities", Alignment.MID, 8f);
            addIllegalCommoditiesHorizontal(info, illegal, w);
        }

        // ── Pressure & Leverage ───────────────────────────────────────────────
        info.addSectionHeading("Pressure & Leverage", Alignment.MID, 10f);
        addPressureBar(info, playerId, f.getId(), w);

        // ── Active Agreements ─────────────────────────────────────────────────
        info.addSectionHeading("Active Agreements", Alignment.MID, 6f);
        addAgreements(info, playerId, f.getId());

        // ── Diplomatic History ────────────────────────────────────────────────
        info.addSectionHeading("Diplomatic History", Alignment.MID, 6f);
        addDiplomacyHistory(info, playerId, f.getId(), false);
    }

    /** Horizontal row of commodity icons with names below, using a manual-Y CustomPanelAPI. */
    private void addIllegalCommoditiesHorizontal(TooltipMakerAPI info, List<String> illegal, float w) {
        float iconSize = 56f;
        float nameH   = 20f;
        float cellW   = iconSize + 12f;
        float rowH    = iconSize + nameH + 4f;
        float totalW  = Math.min(w - 8f, illegal.size() * cellW);

        CustomPanelAPI row = Global.getSettings().createCustom(totalW, rowH, new EmptyPlugin());
        float x = 0f;
        for (String cid : illegal) {
            try {
                CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(cid);
                String displayName = cid.equals("ai_cores") ? "AI Cores" : spec.getName();
                TooltipMakerAPI cell = row.createUIElement(cellW, rowH, false);
                cell.addImage(spec.getIconName(), iconSize, iconSize, 0f);
                cell.addPara(displayName, Misc.getNegativeHighlightColor(), 2f);
                row.addUIElement(cell).inTL(x, 0f);
                x += cellW;
            } catch (Exception ignore) {}
        }
        info.addCustom(row, 6f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RIGHT SIDEBAR — crest, negotiate, allies, enemies
    // ─────────────────────────────────────────────────────────────────────────

    private void buildDetailSidebar(TooltipMakerAPI info, float w) {
        if (selectedFactionId == null) return;
        FactionAPI f = Global.getSector().getFaction(selectedFactionId);
        if (f == null) return;

        // Rectangular crest: full width, 3/4 height ratio
        info.addImage(f.getCrest(), w - 8f, (w - 8f) * 0.75f, 4f);
        info.addSpacer(6f);
        info.addButton("Negotiate", BTN_NEGOTIATE,
                f.getBaseUIColor(), f.getDarkUIColor(),
                Alignment.MID, CutStyle.ALL, w - 8f, 26f, 4f);

        addFactionHeading(info, "Known Allies", f.getBaseUIColor(), 10f);
        addRelatedFactionLinks(info, f, true, w);

        addFactionHeading(info, "Known Enemies", f.getBaseUIColor(), 8f);
        addRelatedFactionLinks(info, f, false, w);
    }

    // ── Detail sections ───────────────────────────────────────────────────────

    private void addLoreDescription(TooltipMakerAPI info, FactionAPI f, float w) {
        String desc = null;

        // Primary: descriptions.csv with type FACTION, id = faction id
        try {
            com.fs.starfarer.api.loading.Description d =
                    Global.getSettings().getDescription(f.getId(),
                            com.fs.starfarer.api.loading.Description.Type.FACTION);
            if (d != null) desc = d.getText1();
        } catch (Exception ignore) {}

        // Fallback: "custom":{} sub-object in .faction file
        if (desc == null || desc.trim().isEmpty()) {
            try {
                org.json.JSONObject custom = f.getCustom();
                if (custom != null) desc = custom.optString("description", null);
            } catch (Exception ignore) {}
        }

        if (desc == null || desc.trim().isEmpty()) desc = null;
        info.addPara(desc != null ? desc : "No description on record.", Misc.getGrayColor(), 2f);
    }

    private void addPressureBar(TooltipMakerAPI info, String playerId, String factionId, float w) {
        PressureManager pm = PressureManager.get();
        if (pm == null) { info.addPara("Pressure data unavailable.", Misc.getGrayColor(), 4f); return; }

        float ourPressure   = pm.getPressure(playerId, factionId);
        float theirPressure = pm.getPressure(factionId, playerId);
        float net           = ourPressure - theirPressure;
        float maxVal        = Math.max(1f, Math.max(ourPressure, theirPressure));

        int barLen = 20, centreIdx = barLen / 2;
        int shift  = Math.round((net / (maxVal * 2f)) * barLen);
        int filled = Math.max(0, Math.min(barLen, centreIdx + shift));

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLen; i++) {
            if (i == centreIdx) bar.append('|');
            else if (net > 0 && i >= centreIdx && i < filled) bar.append('#');
            else if (net < 0 && i >= filled && i < centreIdx) bar.append('#');
            else bar.append('.');
        }

        Color barColor = net >= 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
        LabelAPI lbl = info.addPara("THEM [" + bar + "] YOU", 4f);
        lbl.setHighlight(bar.toString());
        lbl.setHighlightColor(barColor);
        info.addPara(String.format("Ours: %.0f  |  Theirs: %.0f  |  Net: %+.0f",
                ourPressure, theirPressure, net), Misc.getGrayColor(), 2f);
    }

    private void addAgreements(TooltipMakerAPI info, String playerId, String factionId) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) { info.addPara("No data.", Misc.getGrayColor(), 4f); return; }
        AgreementManager am = mgr.getAgreementManager();
        List<nex4x.agreements.Agreement> allMine = am.getAgreementsFor(playerId);
        List<nex4x.agreements.Agreement> agreements = new ArrayList<nex4x.agreements.Agreement>();
        for (nex4x.agreements.Agreement a : allMine)
            if (factionId.equals(a.getOtherFaction(playerId))) agreements.add(a);
        if (agreements.isEmpty()) { info.addPara("No active agreements.", Misc.getGrayColor(), 4f); return; }
        for (nex4x.agreements.Agreement a : agreements)
            info.addPara("- " + a.getType().displayName, Misc.getHighlightColor(), 2f);
    }

    public void addDiplomacyHistory(TooltipMakerAPI info, String playerId, String factionId,
                                    boolean fullList) {
        List<DiploEventEntry> events = getDiploEvents(playerId, factionId);
        if (events.isEmpty()) { info.addPara("No recent diplomatic activity.", Misc.getGrayColor(), 4f); return; }
        int limit = fullList ? events.size() : 5, shown = 0;
        for (DiploEventEntry e : events) {
            if (shown >= limit) break;
            shown++;
            String deltaStr = String.format("%+.0f", e.delta);
            Color c = e.delta >= 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
            LabelAPI l = info.addPara(String.format("%dd", Math.round(e.daysAgo)) + "  " + e.description + "  " + deltaStr, 2f);
            l.setHighlight(deltaStr);
            l.setHighlightColor(c);
        }
        int rem = events.size() - limit;
        if (!fullList && rem > 0) info.addPara("+" + rem + " more events", Misc.getGrayColor(), 2f);
    }

    private static final float ALLY_CARD_H = 38f;

    /** Threshold is on raw FactionAPI scale (-1..1). Centralize via Nex4xRelations if expanded. */
    private void addRelatedFactionLinks(TooltipMakerAPI info, FactionAPI f,
                                        boolean allies, float w) {
        List<FactionAPI> related = new ArrayList<FactionAPI>();
        float threshold = allies ? 0.5f : -0.5f;
        for (FactionAPI other : Global.getSector().getAllFactions()) {
            if (other.getId().equals(f.getId()) || other.isNeutralFaction()) continue;
            if (!hasMarkets(other.getId())) continue;
            float r = f.getRelationship(other.getId());
            if (allies ? r >= threshold : r <= threshold) related.add(other);
        }
        if (related.isEmpty()) {
            info.addPara(allies ? "No known allies." : "No known enemies.", Misc.getGrayColor(), 4f);
            return;
        }
        float cardW = w - 4f;
        float totalH = related.size() * (ALLY_CARD_H + 2f);
        CustomPanelAPI sub = Global.getSettings().createCustom(cardW, totalH, null);
        float y = 0f;
        for (FactionAPI o : related) {
            boolean oSel = o.getId().equals(selectedFactionId);
            Color oFill  = oSel ? CARD_BG_SELECTED : CARD_BG_NORMAL;
            float imgH   = 30f;
            float imgW   = imgH * 4f / 3f; // 4:3 rectangle
            float imgPad = (ALLY_CARD_H - imgH) / 2f;

            TooltipMakerAPI btnEl2 = sub.createUIElement(cardW, ALLY_CARD_H, false);
            btnEl2.addButton(" ", BTN_ALLY_NAV + o.getId(),
                    o.getBaseUIColor(), oFill, Alignment.MID, CutStyle.ALL, cardW, ALLY_CARD_H, 0f);
            sub.addUIElement(btnEl2).inTL(0f, y);

            TooltipMakerAPI imgEl2 = sub.createUIElement(imgW, imgH, false);
            imgEl2.addImage(o.getCrest(), imgW, imgH, 0f);
            sub.addUIElement(imgEl2).inTL(2f, y + imgPad);

            float textX2 = imgW + 4f;
            float textW2 = cardW - textX2 - 2f;
            TooltipMakerAPI textEl2 = sub.createUIElement(textW2, ALLY_CARD_H, false);
            textEl2.addPara(o.getDisplayName(), o.getBaseUIColor(), 4f);
            textEl2.addRelationshipBar(o, 2f);
            sub.addUIElement(textEl2).inTL(textX2, y);
            y += ALLY_CARD_H + 2f;
        }
        info.addCustom(sub, 4f);
    }

    // ── Diplomatic event retrieval ────────────────────────────────────────────

    private static class DiploEventEntry {
        String description; float delta, daysAgo;
        DiploEventEntry(String d, float delta, float days) { this.description = d; this.delta = delta; this.daysAgo = days; }
    }

    private List<DiploEventEntry> getDiploEvents(String playerId, String factionId) {
        List<DiploEventEntry> out = new ArrayList<DiploEventEntry>();
        try {
            // FQN here disambiguates from nex4x.ui.DiplomacyIntel (same package).
            List<IntelInfoPlugin> all = Global.getSector().getIntelManager()
                    .getIntel(exerelin.campaign.intel.diplomacy.DiplomacyIntel.class);
            for (IntelInfoPlugin intel : all) {
                if (!(intel instanceof exerelin.campaign.intel.diplomacy.DiplomacyIntel)) continue;
                exerelin.campaign.intel.diplomacy.DiplomacyIntel di =
                        (exerelin.campaign.intel.diplomacy.DiplomacyIntel) intel;
                Object v1 = RolfLectionUtil.getPrivateVariable("factionId1", di);
                Object v2 = RolfLectionUtil.getPrivateVariable("factionId2", di);
                if (!(v1 instanceof String) || !(v2 instanceof String)) continue;
                String f1 = (String) v1, f2 = (String) v2;
                if (!((f1.equals(playerId) || f2.equals(playerId)) && (f1.equals(factionId) || f2.equals(factionId)))) continue;
                float days = di.getDaysSincePlayerVisible();
                if (days > 180f) continue;
                float delta = di.getReputation() != null ? di.getReputation().delta : 0f;
                out.add(new DiploEventEntry(di.getName(), delta, days));
            }
        } catch (Throwable ignore) {}
        Collections.sort(out, new Comparator<DiploEventEntry>() {
            public int compare(DiploEventEntry a, DiploEventEntry b) { return Float.compare(a.daysAgo, b.daysAgo); }
        });
        return out;
    }

    // ── Button handling ───────────────────────────────────────────────────────

    public boolean handleButton(Object buttonId, Runnable r) {
        if (BTN_SORT.equals(buttonId)) { sortMode = sortMode.next(); if (r != null) r.run(); return true; }
        if (BTN_DIR.equals(buttonId))  { sortAscending = !sortAscending; if (r != null) r.run(); return true; }
        if (BTN_NEGOTIATE.equals(buttonId) && selectedFactionId != null) {
            final String fid = selectedFactionId;
            Nex4xDeferredUi.runNextFrame(new Runnable() {
                public void run() { NegotiationPanel.openScaled(fid, !nex4x.leaders.LeaderAccessGate.isOpen(fid)); }
            });
            return true;
        }
        if (buttonId instanceof String) {
            String id = (String) buttonId;
            if (id.startsWith(BTN_FACTION) || id.startsWith(BTN_ALLY_NAV)) {
                selectedFactionId = id.substring(id.startsWith(BTN_FACTION) ? BTN_FACTION.length() : BTN_ALLY_NAV.length());
                if (r != null) r.run();
                return true;
            }
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final org.apache.log4j.Logger log = Global.getLogger(DiplomacyTabOverlayModel.class);

    private List<FactionAPI> getBrowsableFactions() {
        List<FactionAPI> out = new ArrayList<FactionAPI>();
        String playerId = Global.getSector().getPlayerFaction().getId();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.isNeutralFaction() || f.getId().equals(playerId)) continue;
            if (f.getId().equals("derelict") || f.getId().equals("nex_derelict")) continue;
            if (!hasMarkets(f.getId())) continue;
            out.add(f);
        }
        log.info("[Nex4x] getBrowsableFactions: found " + out.size() + " factions: " + getFactionIds(out));
        return out;
    }

    private static String getFactionIds(List<FactionAPI> factions) {
        StringBuilder sb = new StringBuilder();
        for (FactionAPI f : factions) { if (sb.length() > 0) sb.append(", "); sb.append(f.getId()); }
        return sb.toString();
    }

    private void sortFactions(List<FactionAPI> factions, String playerId) {
        final int dir = sortAscending ? 1 : -1;
        final String pid = playerId;
        switch (sortMode) {
            case POWER:
                Collections.sort(factions, new Comparator<FactionAPI>() {
                    public int compare(FactionAPI a, FactionAPI b) {
                        float sa = FactionPowerRankings.economicScore(a.getId()) + FactionPowerRankings.militaryScore(a.getId()) + FactionPowerRankings.expansionScore(a.getId());
                        float sb = FactionPowerRankings.economicScore(b.getId()) + FactionPowerRankings.militaryScore(b.getId()) + FactionPowerRankings.expansionScore(b.getId());
                        return dir * Float.compare(sb, sa);
                    }
                }); break;
            case RELATION:
                Collections.sort(factions, new Comparator<FactionAPI>() {
                    public int compare(FactionAPI a, FactionAPI b) { return dir * Float.compare(a.getRelationship(pid), b.getRelationship(pid)); }
                }); break;
            default: sortFactionsByRelation(factions, playerId);
        }
    }

    private void sortFactionsByRelation(List<FactionAPI> factions, final String playerId) {
        final int dir = sortAscending ? -1 : 1;
        Collections.sort(factions, new Comparator<FactionAPI>() {
            public int compare(FactionAPI a, FactionAPI b) { return dir * Float.compare(b.getRelationship(playerId), a.getRelationship(playerId)); }
        });
    }

    private static boolean hasMarkets(String factionId) {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy())
            if (factionId.equals(m.getFactionId())) return true;
        return false;
    }

    private static String getArchetype(String factionId) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return "";
        Archetype a = mgr.getGrandStrategy().getArchetype(factionId);
        return a == null ? "" : a.displayName;
    }

    private static List<String> getIllegalCommodities(FactionAPI f) {
        List<String> out = new ArrayList<String>();
        boolean aiCoresAdded = false;
        try {
            for (String c : Global.getSector().getEconomy().getAllCommodityIds()) {
                if (!f.isIllegal(c)) continue;
                if (c.equals("omega_core")) continue;
                try {
                    CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(c);
                    // Group ALL ai_core-tagged commodities (alpha/beta/gamma/command subroutines/
                    // infected cores/any modded variants) under a single "AI Cores" entry.
                    if (spec != null && spec.hasTag("ai_core")) {
                        if (!aiCoresAdded) { out.add("ai_cores"); aiCoresAdded = true; }
                        continue;
                    }
                } catch (Exception ignore) {}
                out.add(c);
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static void addFactionHeading(TooltipMakerAPI info, String label, Color color, float pad) {
        info.addSpacer(pad);
        info.addPara("-- " + label + " --", color, 0f);
    }

    static Color relColor(float rel) {
        if (rel >= 0.5f) return Misc.getPositiveHighlightColor();
        if (rel >= 0f)   return Misc.getHighlightColor();
        if (rel >= -0.5f) return Misc.getNegativeHighlightColor();
        return Misc.getDarkHighlightColor();
    }

    static String relLabel(float rel) {
        if (rel >= 0.5f)  return "Friendly";
        if (rel >= 0.1f)  return "Welcoming";
        if (rel >= -0.1f) return "Neutral";
        if (rel >= -0.5f) return "Inhospitable";
        return "Hostile";
    }

    // ── Plugins ───────────────────────────────────────────────────────────────

    /** Routes button presses from the list panel up to the model. */
    private class ListPlugin implements CustomUIPanelPlugin {
        @Override public void positionChanged(PositionAPI p) {}
        @Override public void renderBelow(float a) {}
        @Override public void render(float a) {}
        @Override public void advance(float a) {}
        @Override public void processInput(List<InputEventAPI> e) {}
        @Override public void buttonPressed(Object id) { handleButton(id, refresh); }
    }

    /** No-op plugin for sub-panels that need no interaction (e.g. icon rows). */
    private static class EmptyPlugin implements CustomUIPanelPlugin {
        @Override public void positionChanged(PositionAPI p) {}
        @Override public void renderBelow(float a) {}
        @Override public void render(float a) {}
        @Override public void advance(float a) {}
        @Override public void processInput(List<InputEventAPI> e) {}
        @Override public void buttonPressed(Object id) {}
    }

    // ── Hover tooltip for recent diplo events ─────────────────────────────────

    private class DiploEventTooltip implements TooltipMakerAPI.TooltipCreator {
        private final String factionId, playerId;
        DiploEventTooltip(String fid, String pid) { factionId = fid; playerId = pid; }

        public boolean isTooltipExpandable(Object p) { return false; }
        public float getTooltipWidth(Object p) { return 320f; }

        public void createTooltip(TooltipMakerAPI t, boolean expanded, Object p) {
            FactionAPI f = Global.getSector().getFaction(factionId);
            if (f != null) t.addPara("Recent events with " + f.getDisplayName(), f.getBaseUIColor(), 0f);
            List<DiploEventEntry> events = getDiploEvents(playerId, factionId);
            if (events.isEmpty()) { t.addPara("No recent diplomatic activity (last 6 months).", Misc.getGrayColor(), 4f); return; }
            int shown = 0;
            for (DiploEventEntry e : events) {
                if (shown++ >= 5) break;
                String ds = String.format("%+.0f", e.delta);
                Color c = e.delta >= 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
                LabelAPI l = t.addPara(String.format("%dd", Math.round(e.daysAgo)) + "  " + e.description + "  " + ds, 2f);
                l.setHighlight(ds); l.setHighlightColor(c);
            }
            int rem = events.size() - 5;
            if (rem > 0) t.addPara("+" + rem + " more", Misc.getGrayColor(), 2f);
        }
    }
}
