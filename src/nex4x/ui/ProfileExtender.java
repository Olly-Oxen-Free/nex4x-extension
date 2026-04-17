package nex4x.ui;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import nex4x.badges.BadgeType;
import nex4x.badges.FactionBadges;
import nex4x.data.*;
import nex4x.managers.Nex4xManager;
import nex4x.memory.FactionMemory;
import nex4x.memory.FactionMemoryStore;
import nex4x.memory.MemoryVisibility;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Faction Dossier" intel — companion to DiplomacyProfileIntel.
 * Shows beliefs, memories, badges, and politics summary for one faction.
 * One instance per live faction, created on game load.
 */
public class ProfileExtender extends BaseIntelPlugin {

    private static final Object BUTTON_PROPOSE_DEAL = "nex4x_propose_deal";

    private final String factionId;

    public ProfileExtender(String factionId) {
        this.factionId = factionId;
    }

    private FactionAPI getFaction() {
        return Global.getSector().getFaction(factionId);
    }

    @Override
    public boolean hasSmallDescription() { return false; }
    @Override
    public boolean hasLargeDescription() { return true; }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float opad = 10f;
        float sectionPad = 15f;

        TooltipMakerAPI info = panel.createUIElement(width, height, true);
        FactionAPI faction = getFaction();
        if (faction == null) return;

        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        MemoryVisibility access = MemoryVisibility.getAccessLevel(playerFactionId, factionId);

        // Header
        info.addSectionHeading(faction.getDisplayName() + " — Political Dossier",
                faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);

        // Section: Reputation Badges
        addBadgesSection(info, opad);

        // Section: Beliefs
        addBeliefsSection(info, access, sectionPad);

        // Section: Political Tendencies
        addTendenciesSection(info, access, sectionPad);

        // Section: Memories
        addMemoriesSection(info, access, sectionPad);

        // Propose Deal button
        info.addSpacer(sectionPad);
        addGenericButton(info, sectionPad, "Propose Deal", BUTTON_PROPOSE_DEAL);

        panel.addUIElement(info).inTL(0, 0);
    }

    private void addBadgesSection(TooltipMakerAPI info, float pad) {
        info.addSectionHeading("REPUTATION", Alignment.MID, pad);

        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return;

        FactionBadges badges = mgr.getBadgeManager().getBadges(factionId);
        Map<BadgeType, Float> active = badges.getActiveBadges();

        if (active.isEmpty()) {
            info.addPara("No notable reputation badges.", 5f);
            return;
        }

        for (Map.Entry<BadgeType, Float> entry : active.entrySet()) {
            BadgeType bt = entry.getKey();
            float remaining = entry.getValue();

            String text = bt.displayName;
            if (remaining > 0) {
                text += String.format(" (%d days remaining)", (int) remaining);
            }

            LabelAPI label = info.addPara(text + " — " + bt.description, 3f);
            label.setHighlight(bt.displayName);
            label.setHighlightColor(bt.color);
        }
    }

    private void addBeliefsSection(TooltipMakerAPI info, MemoryVisibility access, float pad) {
        info.addSectionHeading("BELIEFS", Alignment.MID, pad);

        if (!access.canSeePublicBeliefs()) {
            info.addPara("Intelligence insufficient to assess this faction's core beliefs.",
                    Misc.getGrayColor(), 5f);
            return;
        }

        FactionBeliefs beliefs = FactionBeliefsLoader.getBeliefs(factionId);
        boolean hasContent = false;

        for (FactionBeliefs.BeliefEntry entry : beliefs.getEntries()) {
            // Filter by visibility
            if (entry.visibility == FactionBeliefs.Visibility.SECRET && !access.canSeeSecretBeliefs()) {
                continue;
            }

            BeliefDef def = BeliefRegistry.get(entry.beliefId);
            if (def == null) continue;

            hasContent = true;

            String strengthStr;
            Color strengthColor;
            switch (entry.strength) {
                case 1: strengthStr = "Minor"; strengthColor = Misc.getGrayColor(); break;
                case 2: strengthStr = "Important"; strengthColor = Misc.getHighlightColor(); break;
                case 3: strengthStr = "Existential"; strengthColor = Misc.getNegativeHighlightColor(); break;
                default: strengthStr = "Unknown"; strengthColor = Misc.getGrayColor();
            }

            String visStr = entry.visibility == FactionBeliefs.Visibility.SECRET ? " [SECRET]" : "";
            String text = def.name + visStr + " — " + strengthStr
                    + " (" + def.category.name().toLowerCase() + ")";

            LabelAPI label = info.addPara(text, 3f);
            label.setHighlight(def.name, strengthStr);
            label.setHighlightColors(getFaction().getBaseUIColor(), strengthColor);
        }

        if (!hasContent) {
            info.addPara("No beliefs identified at current intelligence level.",
                    Misc.getGrayColor(), 5f);
        }
    }

    private void addTendenciesSection(TooltipMakerAPI info, MemoryVisibility access, float pad) {
        info.addSectionHeading("POLITICAL TENDENCIES", Alignment.MID, pad);

        if (!access.canSeeTendencies()) {
            info.addPara("Intelligence insufficient to assess internal politics.",
                    Misc.getGrayColor(), 5f);
            return;
        }

        TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);

        for (TendencyId t : TendencyId.values()) {
            float weight = profile.getWeight(t);
            if (weight <= 0 && access.level < MemoryVisibility.DEEP.level) continue;

            // Text-based bar
            int barLength = Math.round(weight * 3);
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < barLength; i++) bar.append("\u2588");
            for (int i = barLength; i < 30; i++) bar.append("\u2591");

            String weightStr;
            if (access.level >= MemoryVisibility.DEEP.level) {
                weightStr = String.format("%.1f", weight);
            } else {
                weightStr = String.valueOf(Math.round(weight));
            }

            String text = t.displayName + "  " + bar.toString() + "  " + weightStr;
            LabelAPI label = info.addPara(text, 2f);
            label.setHighlight(t.displayName, weightStr);
            label.setHighlightColors(t.color, Misc.getHighlightColor());
        }

        info.addPara("Budget: " + String.format("%.1f", profile.getTotal()) + " / 10.0",
                Misc.getGrayColor(), 5f);
    }

    private void addMemoriesSection(TooltipMakerAPI info, MemoryVisibility access, float pad) {
        info.addSectionHeading("RECENT MEMORIES", Alignment.MID, pad);

        if (!access.canSeeMemories()) {
            info.addPara("Intelligence insufficient to assess this faction's disposition history.",
                    Misc.getGrayColor(), 5f);
            return;
        }

        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return;

        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        FactionMemoryStore store = mgr.getMemoryManager().getStore(factionId, playerFactionId);
        List<FactionMemory> memories = store.getMemories();

        if (memories.isEmpty()) {
            info.addPara("No recorded diplomatic events with your faction.",
                    Misc.getGrayColor(), 5f);
            return;
        }

        int shown = 0;
        for (FactionMemory mem : memories) {
            if (shown >= 10) break;

            MemoryTypeDef typeDef = MemoryTypeRegistry.get(mem.getTypeId());
            if (typeDef == null) continue;

            float impact = mem.getCurrentImpact();
            int strengthPct = Math.round(mem.getStrengthFraction() * 100);

            String impactStr = String.format("%+.0f", impact);
            Color impactColor = impact >= 0
                    ? Misc.getPositiveHighlightColor()
                    : Misc.getNegativeHighlightColor();

            String text = typeDef.name + "  " + impactStr + " disposition";
            if (access.canSeeMemoryDetails()) {
                text += "  (" + strengthPct + "% strength)";
                if (mem.getDetails() != null) {
                    text += "  — " + mem.getDetails();
                }
            }

            LabelAPI label = info.addPara(text, 3f);
            label.setHighlight(typeDef.name, impactStr);
            label.setHighlightColors(Misc.getTextColor(), impactColor);

            shown++;
        }

        if (memories.size() > 10) {
            info.addPara("...and " + (memories.size() - 10) + " older memories",
                    Misc.getGrayColor(), 3f);
        }

        float totalDisp = store.getTotalDispositionModifier();
        String totalStr = String.format("%+.0f", totalDisp);
        Color totalColor = totalDisp >= 0
                ? Misc.getPositiveHighlightColor()
                : Misc.getNegativeHighlightColor();
        LabelAPI totalLabel = info.addPara("Net memory disposition: " + totalStr, 8f);
        totalLabel.setHighlight(totalStr);
        totalLabel.setHighlightColor(totalColor);
    }

    // Intel metadata
    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (BUTTON_PROPOSE_DEAL == buttonId) {
            BasePopUpDialog.popUpDialog(new NegotiationPopUpDialog(factionId), 620, 560);
        }
    }

    @Override
    public String getIcon() {
        FactionAPI faction = getFaction();
        return faction != null ? faction.getCrest() : null;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Diplomacy");
        tags.add("Nex4x");
        return tags;
    }

    @Override
    public String getName() {
        FactionAPI faction = getFaction();
        return (faction != null ? faction.getDisplayName() : factionId) + " Dossier";
    }

    @Override
    public String getSortString() {
        return "Dossier " + getName();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return getFaction();
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public boolean isHidden() {
        return false;
    }
}
