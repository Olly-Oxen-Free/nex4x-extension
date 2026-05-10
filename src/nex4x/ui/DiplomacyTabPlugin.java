package nex4x.ui;

import ashlib.data.plugins.coreui.CommandUIPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import nex4x.leaders.LeaderAccessGate;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Outposts-screen "Diplomacy" tab.
 * Left: faction cards (crest + name + relation bar + Negotiate button).
 * Right: faction detail pane from FactionBrowserPanelModel.
 * Clicking Negotiate opens NegotiationPanel directly (no deferral needed — we are not in intel context).
 */
public class DiplomacyTabPlugin extends CommandUIPlugin {

    private static final String BTN_NEGOTIATE_PREFIX = "dip_tab_neg_";
    private static final String BTN_FACTION_PREFIX   = "nex4x_fb_faction:";

    private final FactionBrowserPanelModel browser = new FactionBrowserPanelModel();

    public DiplomacyTabPlugin(float width, float height) {
        super(width, height);
    }

    @Override
    public void createUI() {
        float width  = mainPanel.getPosition().getWidth();
        float height = mainPanel.getPosition().getHeight();
        float listW  = Math.min(300f, Math.max(220f, width * 0.30f));
        float gap    = 8f;
        float detailW = Math.max(120f, width - listW - gap - 20f);

        TooltipMakerAPI list = mainPanel.createUIElement(listW, height, true);
        buildFactionCardList(list, listW);
        mainPanel.addUIElement(list).inTL(10f, 10f);

        TooltipMakerAPI detail = mainPanel.createUIElement(detailW, height, true);
        browser.addDetailPane(detail, detailW);
        mainPanel.addUIElement(detail).inTL(10f + listW + gap, 10f);
    }

    private void buildFactionCardList(TooltipMakerAPI list, float listW) {
        String playerId = Global.getSector().getPlayerFaction().getId();
        list.addSectionHeading("DIPLOMACY", Alignment.MID, 0f);

        List<FactionAPI> factions = getBrowsableFactions(playerId);
        sortByRelation(factions, playerId);

        for (FactionAPI f : factions) {
            buildFactionCard(list, f, playerId, listW);
        }
    }

    private void buildFactionCard(TooltipMakerAPI list, FactionAPI f, String playerId, float listW) {
        float rel     = f.getRelationship(playerId);
        String relStr = String.format("%+.0f", rel);
        Color relColor = relColor(rel);
        Color base = f.getBaseUIColor();
        Color dark = f.getDarkUIColor();

        TooltipMakerAPI card = list.beginImageWithText(f.getCrest(), 48f);
        card.addPara(f.getDisplayName(), base, 0f);
        card.addPara(repLabel(rel) + " (" + relStr + " / 100)", relColor, 2f);
        card.addPara(FactionBrowserPanelModel.repBarAscii(rel), relColor, 1f);
        list.addImageWithText(4f);

        float btnW = (listW - 16f) / 2f - 2f;
        list.addButton("Negotiate", BTN_NEGOTIATE_PREFIX + f.getId(),
                base, dark, Alignment.MID, CutStyle.ALL, btnW, 20f, 2f);
        list.addButton("Details ▶", BTN_FACTION_PREFIX + f.getId(),
                Misc.getGrayColor(), new Color(20, 20, 20, 255),
                Alignment.MID, CutStyle.ALL, btnW, 20f, 2f);

        list.addSpacer(6f);
    }

    @Override
    public void buttonPressed(Object buttonId) {
        if (buttonId instanceof String) {
            String id = (String) buttonId;

            if (id.startsWith(BTN_NEGOTIATE_PREFIX)) {
                String fid = id.substring(BTN_NEGOTIATE_PREFIX.length());
                boolean viceroy = !LeaderAccessGate.isOpen(fid);
                NegotiationPanel.openScaled(fid, viceroy);
                return;
            }
        }

        browser.handleButton(buttonId, null, null, new Runnable() {
            public void run() {
                clearUI(true);
                createUI();
            }
        });
    }

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

    private void sortByRelation(List<FactionAPI> factions, final String playerId) {
        Collections.sort(factions, new Comparator<FactionAPI>() {
            public int compare(FactionAPI a, FactionAPI b) {
                return Float.compare(b.getRelationship(playerId), a.getRelationship(playerId));
            }
        });
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
