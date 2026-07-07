package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import nex4x.leaders.LeaderAccessGate;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Intel entry: Diplomacy screen.
 * Left column: faction cards (sorted by relation). Right column: detail tabs via FactionBrowserPanelModel.
 * Clicking "Negotiate" on a card opens NegotiationPanel (deferred to avoid getCoreUI context issue).
 */
public class DiplomacyIntel extends BaseIntelPlugin {

    private static final Logger log = Global.getLogger(DiplomacyIntel.class);

    // Button ID prefixes
    private static final String BTN_NEGOTIATE_PREFIX = "dip_neg_";
    // Faction card selection reuses FactionBrowserPanelModel's internal prefix so handleButton picks it up
    private static final String BTN_FACTION_PREFIX   = "nex4x_fb_faction:";

    private final FactionBrowserPanelModel browser = new FactionBrowserPanelModel();

    // ── BaseIntelPlugin boilerplate ───────────────────────────

    @Override public boolean hasSmallDescription() { return false; }
    @Override public boolean hasLargeDescription()  { return true; }

    @Override
    public String getName() { return "Diplomacy"; }

    @Override
    public com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.IntelSortTier getSortTier() {
        return com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.IntelSortTier.TIER_1;
    }

    @Override
    public String getSortString() { return "Diplomacy"; }

    @Override
    public String getIcon() {
        FactionAPI p = Global.getSector().getPlayerFaction();
        return p != null ? p.getCrest() : null;
    }

    @Override
    public boolean isHidden() { return false; }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Diplomacy");
        tags.add("Nex4x");
        tags.add("Factions");
        return tags;
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara("Faction relations, agreements, and negotiation.", 0f);
    }

    // ── Main layout ───────────────────────────────────────────

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float listW   = 270f;
        float gap     = 8f;
        float detailW = Math.max(100f, width - listW - gap);

        // Left: scrollable faction card list
        TooltipMakerAPI list = panel.createUIElement(listW, height, true);
        buildFactionCardList(list, listW);
        panel.addUIElement(list).inTL(0f, 0f);

        // Right: detail pane (delegated to FactionBrowserPanelModel)
        TooltipMakerAPI detail = panel.createUIElement(detailW, height, true);
        browser.addDetailPane(detail, detailW);
        panel.addUIElement(detail).inTL(listW + gap, 0f);
    }

    // ── Faction card list ─────────────────────────────────────

    private void buildFactionCardList(TooltipMakerAPI list, float listW) {
        String playerId = Global.getSector().getPlayerFaction().getId();
        FactionAPI player = Global.getSector().getPlayerFaction();
        Color pb = player.getBaseUIColor();
        Color pd = player.getDarkUIColor();

        list.addSectionHeading("FACTIONS", Alignment.MID, 0f);

        List<FactionAPI> factions = getBrowsableFactions(playerId);
        sortByRelation(factions, playerId);

        for (FactionAPI f : factions) {
            buildFactionCard(list, f, playerId, listW);
        }
    }

    private void buildFactionCard(TooltipMakerAPI list, FactionAPI f, String playerId, float listW) {
        float rel     = f.getRelationship(playerId);
        String relStr = String.format("%+d", nex4x.util.Nex4xRelations.toPercentInt(rel));
        Color relColor = relColor(rel);
        Color base = f.getBaseUIColor();
        Color dark = f.getDarkUIColor();

        // Crest + name + relation in image-with-text layout
        TooltipMakerAPI card = list.beginImageWithText(f.getCrest(), 56f);
        card.addPara(f.getDisplayName(), base, 0f);
        card.addPara(repLabel(rel) + " (" + relStr + " / 100)", relColor, 2f);
        card.addPara(FactionBrowserPanelModel.repBarAscii(nex4x.util.Nex4xRelations.toPercent(rel)), relColor, 1f);
        list.addImageWithText(4f);

        // Action buttons
        float btnW = (listW - 16f) / 2f - 2f;
        list.addButton("Negotiate", BTN_NEGOTIATE_PREFIX + f.getId(),
                base, dark, Alignment.MID, CutStyle.ALL, btnW, 20f, 2f);
        list.addButton("Details ▶", BTN_FACTION_PREFIX + f.getId(),
                Misc.getGrayColor(), new Color(20, 20, 20, 255),
                Alignment.MID, CutStyle.ALL, btnW, 20f, 2f);

        list.addSpacer(6f);
    }

    // ── Button dispatch ───────────────────────────────────────

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId instanceof String) {
            String id = (String) buttonId;

            if (id.startsWith(BTN_NEGOTIATE_PREFIX)) {
                final String fid = id.substring(BTN_NEGOTIATE_PREFIX.length());
                Nex4xDeferredUi.runNextFrame(new Runnable() {
                    public void run() {
                        boolean viceroy = !LeaderAccessGate.isOpen(fid);
                        NegotiationPanel.openScaled(fid, viceroy);
                    }
                });
                return;
            }
        }

        // Handle AgreementIntelEmbed buttons, faction/tab selection, etc.
        if (AgreementIntelEmbed.handleButton(buttonId, ui, this)) return;
        browser.handleButton(buttonId, ui, this, null);
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (AgreementIntelEmbed.doesButtonHaveConfirmDialog(buttonId)) return true;
        return super.doesButtonHaveConfirmDialog(buttonId);
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (AgreementIntelEmbed.doesButtonHaveConfirmDialog(buttonId)) {
            AgreementIntelEmbed.createConfirmationPrompt(buttonId, prompt);
        } else {
            super.createConfirmationPrompt(buttonId, prompt);
        }
    }

    // ── Helpers ───────────────────────────────────────────────

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

    /** rawRel is FactionAPI.getRelationship() in [-1..1]; thresholds are percent. */
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
