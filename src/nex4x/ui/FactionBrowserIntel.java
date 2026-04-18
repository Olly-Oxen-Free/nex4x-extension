package nex4x.ui;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import nex4x.ai.StrategicGoalManager;
import nex4x.ai.archetype.Archetype;
import nex4x.ai.goals.StrategicGoal;
import nex4x.badges.BadgeType;
import nex4x.badges.FactionBadges;
import nex4x.data.*;
import nex4x.managers.Nex4xManager;
import nex4x.memory.FactionMemory;
import nex4x.memory.FactionMemoryStore;
import nex4x.memory.MemoryVisibility;

import java.awt.Color;
import java.util.*;

/**
 * Unified 4X-style faction browser intel.
 * Replaces per-faction ProfileExtender dossiers with a single list+detail UI.
 *
 * Layer 1: faction list (row = name, relation, strength, archetype, current action)
 * Layer 2: detail pane with tabs (Overview / Profile / Strategic AI / Memory / Relations)
 * Layer 3: negotiation dialog (launched from Overview button)
 */
public class FactionBrowserIntel extends BaseIntelPlugin {

    public enum Tab {
        OVERVIEW("Overview"),
        PROFILE("Profile"),
        STRATEGIC("Strategic AI"),
        MEMORY("Memory"),
        RELATIONS("Relations");

        public final String displayName;
        Tab(String s) { displayName = s; }
    }

    private static final String BUTTON_FACTION_PREFIX = "nex4x_fb_faction:";
    private static final String BUTTON_TAB_PREFIX = "nex4x_fb_tab:";
    private static final Object BUTTON_NEGOTIATE = "nex4x_fb_negotiate";

    private String selectedFactionId;
    private Tab selectedTab = Tab.OVERVIEW;

    @Override
    public boolean hasSmallDescription() { return false; }
    @Override
    public boolean hasLargeDescription() { return true; }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float pad = 6f;
        float listWidth = 360f;
        float detailWidth = width - listWidth - pad * 2;

        TooltipMakerAPI list = panel.createUIElement(listWidth, height, true);
        addFactionList(list);
        panel.addUIElement(list).inTL(0, 0);

        TooltipMakerAPI detail = panel.createUIElement(detailWidth, height, true);
        addDetailPane(detail, detailWidth);
        panel.addUIElement(detail).rightOfTop(list, pad);
    }

    // Layer 1 — faction list

    private void addFactionList(TooltipMakerAPI info) {
        info.addSectionHeading("FACTIONS", Alignment.MID, 0f);

        List<FactionAPI> factions = getBrowsableFactions();
        sortFactionsByRelation(factions);

        String playerId = Global.getSector().getPlayerFaction().getId();
        for (FactionAPI f : factions) {
            addFactionRow(info, f, playerId);
        }
    }

    private List<FactionAPI> getBrowsableFactions() {
        List<FactionAPI> out = new ArrayList<FactionAPI>();
        String playerId = Global.getSector().getPlayerFaction().getId();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.isNeutralFaction()) continue;
            if (f.getId().equals(playerId)) continue;
            if (f.getId().equals("derelict") || f.getId().equals("nex_derelict")) continue;
            if (!hasMarkets(f.getId())) continue;
            out.add(f);
        }
        return out;
    }

    private void sortFactionsByRelation(List<FactionAPI> factions) {
        final FactionAPI player = Global.getSector().getPlayerFaction();
        Collections.sort(factions, new Comparator<FactionAPI>() {
            public int compare(FactionAPI a, FactionAPI b) {
                return Float.compare(b.getRelationship(player.getId()),
                                     a.getRelationship(player.getId()));
            }
        });
    }

    private void addFactionRow(TooltipMakerAPI info, FactionAPI f, String playerId) {
        float rel = f.getRelationship(playerId);
        String relStr = String.format("%+.0f", rel * 100f);
        Color relColor = rel >= 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();

        String archetypeStr = getArchetypeDisplay(f.getId());
        String actionStr = getCurrentActionDisplay(f.getId());
        int strength = getStrengthTier(f.getId());

        Color base = f.getBaseUIColor();
        Color dark = f.getDarkUIColor();

        String rowText = f.getDisplayName()
                + "  " + relStr
                + "  [Str " + strength + "]"
                + (archetypeStr.isEmpty() ? "" : "  " + archetypeStr);

        info.addButton(rowText, BUTTON_FACTION_PREFIX + f.getId(),
                base, dark, Alignment.LMID, CutStyle.TL_BR, 340f, 22f, 2f);
        if (!actionStr.isEmpty()) {
            LabelAPI al = info.addPara("  → " + actionStr, 1f);
            al.setColor(Misc.getGrayColor());
        }
        if (!relStr.isEmpty()) {
            // highlight color applied via setHighlight on row — button doesn't support per-text color
        }
    }

    private boolean hasMarkets(String factionId) {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId())) return true;
        }
        return false;
    }

    private String getArchetypeDisplay(String factionId) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return "";
        Archetype a = mgr.getGrandStrategy().getArchetype(factionId);
        return a == null ? "" : a.name().replace('_', ' ');
    }

    private String getCurrentActionDisplay(String factionId) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return "";
        StrategicGoalManager gm = mgr.getGoalManager(factionId);
        if (gm == null || gm.getActiveGoals().isEmpty()) return "";
        StrategicGoal top = gm.getActiveGoals().get(0);
        String tgt = top.targetFactionId != null ? " → " + top.targetFactionId : "";
        return top.type.displayName + tgt;
    }

    private int getStrengthTier(String factionId) {
        int markets = 0;
        float fleetFP = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId())) {
                markets++;
                fleetFP += m.getSize();
            }
        }
        if (markets == 0) return 0;
        if (markets >= 12 || fleetFP >= 60) return 5;
        if (markets >= 8 || fleetFP >= 40) return 4;
        if (markets >= 5 || fleetFP >= 25) return 3;
        if (markets >= 3 || fleetFP >= 15) return 2;
        return 1;
    }

    // Layer 2 — detail pane

    private void addDetailPane(TooltipMakerAPI info, float width) {
        if (selectedFactionId == null) {
            info.addPara("Select a faction on the left to view details.",
                    Misc.getGrayColor(), 10f);
            return;
        }
        FactionAPI f = Global.getSector().getFaction(selectedFactionId);
        if (f == null) {
            info.addPara("Faction data unavailable.", Misc.getGrayColor(), 10f);
            return;
        }

        info.addSectionHeading(f.getDisplayName(),
                f.getBaseUIColor(), f.getDarkUIColor(), Alignment.MID, 0f);

        addTabBar(info, f);

        switch (selectedTab) {
            case OVERVIEW:  addOverviewTab(info, f);  break;
            case PROFILE:   addProfileTab(info, f);   break;
            case STRATEGIC: addStrategicTab(info, f); break;
            case MEMORY:    addMemoryTab(info, f);    break;
            case RELATIONS: addRelationsTab(info, f); break;
        }
    }

    private void addTabBar(TooltipMakerAPI info, FactionAPI f) {
        Color base = f.getBaseUIColor();
        Color dark = f.getDarkUIColor();
        for (Tab t : Tab.values()) {
            boolean selected = (t == selectedTab);
            Color btnBase = selected ? base : Misc.getGrayColor();
            info.addButton(t.displayName, BUTTON_TAB_PREFIX + t.name(),
                    btnBase, dark, Alignment.MID, CutStyle.ALL,
                    110f, 20f, t == Tab.values()[0] ? 8f : 2f);
        }
        info.addSpacer(6f);
    }

    private void addOverviewTab(TooltipMakerAPI info, FactionAPI f) {
        String playerId = Global.getSector().getPlayerFaction().getId();
        float rel = f.getRelationship(playerId);
        String relStr = String.format("%+.0f", rel * 100f);
        info.addPara("Relation: %s", 10f, Misc.getHighlightColor(), relStr);

        try {
            float weariness = DiplomacyManager.getWarWeariness(f.getId(), true);
            info.addPara("War weariness: %s", 3f, Misc.getHighlightColor(),
                    String.format("%.0f", weariness));
        } catch (Exception ignore) {}

        String archetype = getArchetypeDisplay(f.getId());
        if (!archetype.isEmpty()) {
            info.addPara("Current archetype: %s", 3f, Misc.getHighlightColor(), archetype);
        }
        String action = getCurrentActionDisplay(f.getId());
        if (!action.isEmpty()) {
            info.addPara("Current focus: %s", 3f, Misc.getHighlightColor(), action);
        }

        int strength = getStrengthTier(f.getId());
        info.addPara("Strength tier: %s/5", 3f, Misc.getHighlightColor(),
                String.valueOf(strength));

        info.addSpacer(10f);
        info.addButton("Open Negotiation", BUTTON_NEGOTIATE,
                f.getBaseUIColor(), f.getDarkUIColor(),
                Alignment.MID, CutStyle.ALL, 200f, 24f, 4f);
    }

    private void addProfileTab(TooltipMakerAPI info, FactionAPI f) {
        String playerId = Global.getSector().getPlayerFaction().getId();
        MemoryVisibility access = MemoryVisibility.getAccessLevel(playerId, f.getId());

        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr != null) {
            info.addSectionHeading("REPUTATION", Alignment.MID, 6f);
            FactionBadges badges = mgr.getBadgeManager().getBadges(f.getId());
            Map<BadgeType, Float> active = badges.getActiveBadges();
            if (active.isEmpty()) {
                info.addPara("No notable badges.", Misc.getGrayColor(), 3f);
            } else {
                for (Map.Entry<BadgeType, Float> e : active.entrySet()) {
                    String text = e.getKey().displayName + " — " + e.getKey().description;
                    if (e.getValue() > 0) text += " (" + (int) e.getValue().floatValue() + "d)";
                    LabelAPI l = info.addPara(text, 2f);
                    l.setHighlight(e.getKey().displayName);
                    l.setHighlightColor(e.getKey().color);
                }
            }
        }

        info.addSectionHeading("BELIEFS", Alignment.MID, 10f);
        if (!access.canSeePublicBeliefs()) {
            info.addPara("Intelligence insufficient.", Misc.getGrayColor(), 3f);
        } else {
            FactionBeliefs beliefs = FactionBeliefsLoader.getBeliefs(f.getId());
            for (FactionBeliefs.BeliefEntry e : beliefs.getEntries()) {
                if (e.visibility == FactionBeliefs.Visibility.SECRET
                        && !access.canSeeSecretBeliefs()) continue;
                BeliefDef def = BeliefRegistry.get(e.beliefId);
                if (def == null) continue;
                String vis = e.visibility == FactionBeliefs.Visibility.SECRET ? " [SECRET]" : "";
                String strength = e.strength == 3 ? "Existential"
                        : e.strength == 2 ? "Important" : "Minor";
                LabelAPI l = info.addPara(def.name + vis + " — " + strength, 2f);
                l.setHighlight(def.name);
                l.setHighlightColor(f.getBaseUIColor());
            }
        }

        info.addSectionHeading("TENDENCIES", Alignment.MID, 10f);
        if (!access.canSeeTendencies()) {
            info.addPara("Intelligence insufficient.", Misc.getGrayColor(), 3f);
        } else {
            TendencyProfile profile = TendencyProfileLoader.getProfile(f.getId());
            for (TendencyId t : TendencyId.values()) {
                float w = profile.getWeight(t);
                if (w <= 0) continue;
                int barLen = Math.round(w * 3);
                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < barLen; i++) bar.append("\u2588");
                for (int i = barLen; i < 20; i++) bar.append("\u2591");
                LabelAPI l = info.addPara(t.displayName + "  " + bar + "  "
                        + String.format("%.1f", w), 2f);
                l.setHighlight(t.displayName);
                l.setHighlightColor(t.color);
            }
        }
    }

    private void addStrategicTab(TooltipMakerAPI info, FactionAPI f) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) {
            info.addPara("Manager unavailable.", Misc.getGrayColor(), 6f);
            return;
        }

        info.addSectionHeading("ACTIVE GOALS", Alignment.MID, 6f);
        StrategicGoalManager gm = mgr.getGoalManager(f.getId());
        if (gm == null || gm.getActiveGoals().isEmpty()) {
            info.addPara("No active goals tracked.", Misc.getGrayColor(), 3f);
        } else {
            int i = 0;
            for (StrategicGoal g : gm.getActiveGoals()) {
                if (i++ >= 8) break;
                String line = String.format("%d. %s  (I=%.0f U=%.0f P=%.0f)",
                        i, g.type.displayName, g.getImportance(), g.getUrgency(),
                        g.getEffectivePriority());
                if (g.targetFactionId != null) line += " vs " + g.targetFactionId;
                info.addPara(line, 2f);
            }
        }

        info.addSectionHeading("COMMITMENT", Alignment.MID, 10f);
        String archetype = getArchetypeDisplay(f.getId());
        info.addPara("Archetype: %s", 3f, Misc.getHighlightColor(),
                archetype.isEmpty() ? "—" : archetype);
    }

    private void addMemoryTab(TooltipMakerAPI info, FactionAPI f) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) {
            info.addPara("Manager unavailable.", Misc.getGrayColor(), 6f);
            return;
        }
        String playerId = Global.getSector().getPlayerFaction().getId();
        MemoryVisibility access = MemoryVisibility.getAccessLevel(playerId, f.getId());

        info.addSectionHeading("RECENT MEMORIES", Alignment.MID, 6f);
        if (!access.canSeeMemories()) {
            info.addPara("Intelligence insufficient.", Misc.getGrayColor(), 3f);
            return;
        }

        FactionMemoryStore store = mgr.getMemoryManager().getStore(f.getId(), playerId);
        List<FactionMemory> memories = store.getMemories();
        if (memories.isEmpty()) {
            info.addPara("No recorded memories.", Misc.getGrayColor(), 3f);
            return;
        }

        int shown = 0;
        for (FactionMemory m : memories) {
            if (shown++ >= 10) break;
            MemoryTypeDef td = MemoryTypeRegistry.get(m.getTypeId());
            String name = td != null ? td.name : m.getTypeId();
            float impact = m.getCurrentImpact();
            String impactStr = String.format("%+.0f", impact);
            Color impactColor = impact >= 0
                    ? Misc.getPositiveHighlightColor()
                    : Misc.getNegativeHighlightColor();
            LabelAPI l = info.addPara(name + "  " + impactStr, 2f);
            l.setHighlight(impactStr);
            l.setHighlightColor(impactColor);
        }
    }

    private void addRelationsTab(TooltipMakerAPI info, FactionAPI f) {
        info.addSectionHeading("RELATIONS WITH OTHERS", Alignment.MID, 6f);
        List<FactionAPI> others = getBrowsableFactions();
        for (FactionAPI o : others) {
            if (o.getId().equals(f.getId())) continue;
            float r = f.getRelationship(o.getId());
            String rStr = String.format("%+.0f", r * 100f);
            Color c = r >= 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
            LabelAPI l = info.addPara(o.getDisplayName() + "  " + rStr, 2f);
            l.setHighlight(rStr);
            l.setHighlightColor(c);
        }
    }

    // Button handling

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId instanceof String) {
            String id = (String) buttonId;
            if (id.startsWith(BUTTON_FACTION_PREFIX)) {
                selectedFactionId = id.substring(BUTTON_FACTION_PREFIX.length());
                selectedTab = Tab.OVERVIEW;
                ui.updateUIForItem(this);
                return;
            }
            if (id.startsWith(BUTTON_TAB_PREFIX)) {
                try {
                    selectedTab = Tab.valueOf(id.substring(BUTTON_TAB_PREFIX.length()));
                } catch (IllegalArgumentException ignore) {}
                ui.updateUIForItem(this);
                return;
            }
        }
        if (BUTTON_NEGOTIATE == buttonId && selectedFactionId != null) {
            BasePopUpDialog.popUpDialog(new NegotiationPopUpDialog(selectedFactionId), 620, 560);
        }
    }

    // Intel metadata

    @Override
    public String getName() { return "Faction Browser"; }

    @Override
    public String getSortString() { return "AAA_FactionBrowser"; }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Diplomacy");
        tags.add("Nex4x");
        tags.add("Factions");
        return tags;
    }

    @Override
    public String getIcon() {
        FactionAPI player = Global.getSector().getPlayerFaction();
        return player != null ? player.getCrest() : null;
    }

    @Override
    public boolean isHidden() { return false; }
}
