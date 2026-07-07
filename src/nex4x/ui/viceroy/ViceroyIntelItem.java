package nex4x.ui.viceroy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Intel-journal entry created when a player purchases intel from a viceroy.
 *
 * The payload is a snapshot captured at purchase time (see {@link IntelPurchaseHandler}), not a
 * live query — so the report reflects the sector state the player paid for. Depth scales by tier:
 *   - basic tiers (LOCATION_TIP, BOUNTY_TARGETS): relations + active wars.
 *   - full tier   (FACTION_GOALS):               + agreements + influence treasury + power rank.
 *
 * Extends BaseIntelPlugin (no abstract methods) rather than BaseMissionIntel to avoid the full
 * mission lifecycle.
 */
public class ViceroyIntelItem extends BaseIntelPlugin {

    private final IntelPurchaseHandler.IntelTier tier;
    private final String marketFactionId;

    // Snapshot payload (all captured at purchase time).
    private final List<String> relationLines;
    private final List<String> warLines;
    private final List<String> agreementLines;
    private final String influenceStr;   // null on basic tiers
    private final String rankStr;         // null on basic tiers

    public ViceroyIntelItem(IntelPurchaseHandler.IntelTier tier, String marketFactionId,
                            List<String> relationLines, List<String> warLines,
                            List<String> agreementLines, String influenceStr, String rankStr) {
        this.tier = tier;
        this.marketFactionId = marketFactionId;
        this.relationLines = relationLines != null ? relationLines : new ArrayList<String>();
        this.warLines = warLines != null ? warLines : new ArrayList<String>();
        this.agreementLines = agreementLines != null ? agreementLines : new ArrayList<String>();
        this.influenceStr = influenceStr;
        this.rankStr = rankStr;
    }

    public IntelPurchaseHandler.IntelTier getTier() { return tier; }
    public String getMarketFactionId() { return marketFactionId; }

    private boolean isFullTier() {
        return tier == IntelPurchaseHandler.IntelTier.FACTION_GOALS;
    }

    private String factionName() {
        FactionAPI f = Global.getSector().getFaction(marketFactionId);
        return f != null ? f.getDisplayName() : marketFactionId;
    }

    @Override
    protected String getName() {
        return tier.displayName + " (" + factionName() + ")";
    }

    @Override
    public boolean hasSmallDescription() { return true; }
    @Override
    public boolean hasLargeDescription() { return false; }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float opad) {
        String name = factionName();
        Color hl = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();

        info.addPara("Dossier on " + name + ", as of the cycle of purchase.", opad,
                new Color[]{hl}, name);

        if (isFullTier() && rankStr != null) {
            info.addPara("Power standing: " + rankStr, opad, new Color[]{hl}, rankStr);
        }
        if (isFullTier() && influenceStr != null) {
            info.addPara("Influence treasury: " + influenceStr, 5f, new Color[]{hl}, influenceStr);
        }

        // Relations (all tiers).
        info.addSectionHeading("Standing with major powers", Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(),
                com.fs.starfarer.api.ui.Alignment.MID, opad);
        if (relationLines.isEmpty()) {
            info.addPara("No notable relations on record.", gray, 3f);
        } else {
            for (String line : relationLines) {
                info.addPara(line, 3f);
            }
        }

        // Active wars (all tiers).
        info.addSectionHeading("Active wars", Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(),
                com.fs.starfarer.api.ui.Alignment.MID, opad);
        if (warLines.isEmpty()) {
            info.addPara("Not currently at war with any major faction.", gray, 3f);
        } else {
            for (String line : warLines) {
                info.addPara(line, 3f, Misc.getNegativeHighlightColor(), line);
            }
        }

        // Agreements (full tier only).
        if (isFullTier()) {
            info.addSectionHeading("Standing agreements", Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(),
                    com.fs.starfarer.api.ui.Alignment.MID, opad);
            if (agreementLines.isEmpty()) {
                info.addPara("No active diplomatic agreements on record.", gray, 3f);
            } else {
                for (String line : agreementLines) {
                    info.addPara(line, 3f);
                }
            }
        }
    }

    @Override
    public String getIcon() {
        FactionAPI f = Global.getSector().getFaction(marketFactionId);
        return f != null ? f.getCrest() : null;
    }

    @Override
    public String getSortString() { return "Purchased Intel"; }

    @Override
    public FactionAPI getFactionForUIColors() {
        FactionAPI f = Global.getSector().getFaction(marketFactionId);
        return f != null ? f : Global.getSector().getPlayerFaction();
    }
}
