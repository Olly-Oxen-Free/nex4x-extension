package nex4x.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import nex4x.ai.StrategicGoalManager;
import nex4x.ai.archetype.Archetype;
import nex4x.ai.archetype.CommitmentLedger;
import nex4x.ai.goals.StrategicGoal;
import nex4x.badges.BadgeType;
import nex4x.badges.FactionBadges;
import nex4x.data.*;
import nex4x.managers.Nex4xManager;
import nex4x.leaders.LeaderAccessGate;
import nex4x.leaders.LeaderProfile;
import nex4x.memory.FactionMemory;
import nex4x.memory.FactionMemoryStore;
import nex4x.memory.MemoryVisibility;
import nex4x.util.FactionMarketUtil;
import nex4x.util.FactionPowerRankings;

import java.awt.Color;
import java.io.Serializable;
import java.util.*;

/**
 * Shared list + detail UI for the faction browser intel entry.
 */
public class FactionBrowserPanelModel implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Tab {
        OVERVIEW("Overview"),
        PROFILE("Profile"),
        STRATEGIC("Strategic AI"),
        MEMORY("Memory"),
        /** Faction-vs-faction relations map (tab label: Factions). */
        RELATIONS("Factions"),
        DIPLOMACY("Diplomacy");

        public final String displayName;
        Tab(String s) { displayName = s; }
    }

    private static final String BUTTON_FACTION_PREFIX = "nex4x_fb_faction:";
    private static final String BUTTON_TAB_PREFIX = "nex4x_fb_tab:";
    private static final Object BUTTON_NEGOTIATE = "nex4x_fb_negotiate";
    private static final String BUTTON_CONTACT_LEADER_PREFIX = "contact_leader_";

    private String selectedFactionId;
    private Tab selectedTab = Tab.OVERVIEW;

    /** @param offsetX offsetY top-left padding (e.g. 10 for command tab) */
    public void render(CustomPanelAPI panel, float width, float height, float offsetX, float offsetY) {
        FactionPowerRankings.rebuild();
        float pad = 6f;
        float listWidth = Math.min(360f, Math.max(200f, width * 0.42f));
        float detailWidth = Math.max(120f, width - listWidth - pad * 2);
        float detailX = listWidth + pad;

        TooltipMakerAPI list = panel.createUIElement(listWidth, height, true);
        addFactionList(list);
        panel.addUIElement(list).inTL(offsetX, offsetY);

        TooltipMakerAPI detail = panel.createUIElement(detailWidth, height, true);
        addDetailPane(detail, detailWidth);
        panel.addUIElement(detail).inTL(offsetX + detailX, offsetY);
    }

    // Layer 1 — faction list

    private void addFactionList(TooltipMakerAPI info) {
        info.addSectionHeading("FACTIONS", Alignment.MID, 0f);

        FactionAPI player = Global.getSector().getPlayerFaction();
        Color pb = player.getBaseUIColor();
        Color pd = player.getDarkUIColor();
        info.addButton("Your faction (internal politics)", BUTTON_FACTION_PREFIX + player.getId(),
                pb, pd, Alignment.LMID, CutStyle.TL_BR, 340f, 22f, 4f);

        List<FactionAPI> factions = getBrowsableFactions();
        sortFactionsByRelation(factions);

        String playerId = player.getId();
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
        String relStr = String.format("%+.0f", rel);
        Color relColor = rel >= 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();

        String archetypeStr = getArchetypeDisplay(f.getId());
        String actionStr = getCurrentActionDisplay(f.getId());
        String rankStr = FactionPowerRankings.getRankLabel(f.getId());

        Color base = f.getBaseUIColor();
        Color dark = f.getDarkUIColor();

        String rowText = f.getDisplayName()
                + "  " + relStr
                + "  " + rankStr
                + (archetypeStr.isEmpty() ? "" : "  " + archetypeStr);

        info.addButton(rowText, BUTTON_FACTION_PREFIX + f.getId(),
                base, dark, Alignment.LMID, CutStyle.TL_BR, 340f, 22f, 2f);
        if (!actionStr.isEmpty()) {
            LabelAPI al = info.addPara("  > " + actionStr, 1f);
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
        return a == null ? "" : a.displayName;
    }

    private String getCurrentActionDisplay(String factionId) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return "";
        StrategicGoalManager gm = mgr.getGoalManager(factionId);
        if (gm == null || gm.getActiveGoals().isEmpty()) return "";
        StrategicGoal top = gm.getActiveGoals().get(0);
        String tgt = top.targetFactionId != null ? " > " + formatFactionName(top.targetFactionId) : "";
        return top.type.displayName + tgt;
    }

    private static String formatFactionName(String factionId) {
        FactionAPI o = Global.getSector().getFaction(factionId);
        return o != null ? o.getDisplayName() : factionId;
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

        addTabBar(info, f, width);

        switch (selectedTab) {
            case OVERVIEW:  addOverviewTab(info, f);  break;
            case PROFILE:   addProfileTab(info, f);   break;
            case STRATEGIC: addStrategicTab(info, f, width); break;
            case MEMORY:    addMemoryTab(info, f);    break;
            case RELATIONS: addRelationsTab(info, f, width); break;
            case DIPLOMACY: addDiplomacyTab(info, f, width); break;
        }
    }

    private void addTabBar(TooltipMakerAPI info, FactionAPI f, float barWidth) {
        Color base = f.getBaseUIColor();
        Color dark = f.getDarkUIColor();
        Tab[] tabs = Tab.values();
        float btnW = Math.max(24f, Math.min(110f, (barWidth - 12f) / (float) tabs.length - 1f));
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            boolean selected = (t == selectedTab);
            Color btnBase = selected ? base : Misc.getGrayColor();
            float padAfter = (i == 0) ? 8f : 2f;
            info.addButton(t.displayName, BUTTON_TAB_PREFIX + t.name(),
                    btnBase, dark, Alignment.MID, CutStyle.ALL,
                    btnW, 20f, padAfter);
        }
        info.addSpacer(6f);
    }

    private void renderLeaderHeader(TooltipMakerAPI info, FactionAPI f) {
        if (f.isPlayerFaction()) {
            info.addPara("Your faction overview.", Misc.getGrayColor(), 4f);
            info.addSpacer(6f);
            return;
        }
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return;
        LeaderProfile leader = mgr.getLeaderRegistry().getProfile(f.getId());
        if (leader == null) return;
        String sprite = leader.portraitSpriteForCampaignImage();
        if (sprite != null && !sprite.isEmpty()) {
            TooltipMakerAPI besidePortrait = info.beginImageWithText(sprite, 96f);
            besidePortrait.addPara(leader.displayName(), 4f);
            besidePortrait.addPara("Personality: " + leader.getPersonality(), 2f);
            info.addImageWithText(4f);
        } else {
            info.addPara(leader.displayName(), 4f);
            info.addPara("Personality: " + leader.getPersonality(), 2f);
        }
        boolean open = LeaderAccessGate.isOpen(f.getId());
        String label = open ? "Contact Leader" : "Contact Viceroy";
        info.addButton(label, BUTTON_CONTACT_LEADER_PREFIX + f.getId(),
                f.getBaseUIColor(), f.getDarkUIColor(),
                Alignment.MID, CutStyle.ALL, 170f, 26f, 6f);
        info.addSpacer(6f);
    }

    private void addOverviewTab(TooltipMakerAPI info, FactionAPI f) {
        if (f.isPlayerFaction()) {
            addPlayerPoliticsOverview(info, f);
            return;
        }
        renderLeaderHeader(info, f);

        String playerId = Global.getSector().getPlayerFaction().getId();
        float rel = f.getRelationship(playerId);
        String relStr = String.format("%+.0f", rel);
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

        info.addPara("Power rank: %s", 3f, Misc.getHighlightColor(),
                FactionPowerRankings.getRankLabel(f.getId()));
        info.addPara(String.format("  Economic score: %.0f  Military: %.0f  Expansion: %.0f",
                        FactionPowerRankings.economicScore(f.getId()),
                        FactionPowerRankings.militaryScore(f.getId()),
                        FactionPowerRankings.expansionScore(f.getId())),
                Misc.getGrayColor(), 2f);

        info.addSpacer(10f);
        info.addButton("Open Negotiation", BUTTON_NEGOTIATE,
                f.getBaseUIColor(), f.getDarkUIColor(),
                Alignment.MID, CutStyle.ALL, 200f, 24f, 4f);
    }

    private void addPlayerPoliticsOverview(TooltipMakerAPI info, FactionAPI f) {
        String factionId = f.getId();
        info.addSectionHeading("INTERNAL POLITICS - " + f.getDisplayName().toUpperCase(),
                f.getBaseUIColor(), f.getDarkUIColor(), Alignment.MID, 8f);
        TendencyProfile profile = TendencyProfileLoader.getProfile(factionId);
        info.addSectionHeading("POLITICAL TENDENCIES", Alignment.MID, 8f);
        for (TendencyId t : TendencyId.values()) {
            float w = profile.getWeight(t);
            if (w <= 0) continue;
            int barLen = Math.min(20, Math.round(w * 3));
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < barLen; i++) bar.append('#');
            for (int i = barLen; i < 20; i++) bar.append('.');
            LabelAPI label = info.addPara(t.displayName + "  " + bar + "  "
                    + String.format("%.1f", w), 2f);
            label.setHighlight(t.displayName);
            label.setHighlightColors(t.color, Misc.getHighlightColor());
        }
        info.addPara("Total: " + String.format("%.1f", profile.getTotal()) + " / 10.0",
                Misc.getGrayColor(), 8f);
        info.addSectionHeading("FACTION TRAITS", Alignment.MID, 8f);
        java.util.List<String> traits = DiplomacyTraits.getFactionTraits(factionId);
        if (traits.isEmpty()) {
            info.addPara("No diplomacy traits.", 4f);
        } else {
            for (String traitId : traits) {
                DiplomacyTraits.TraitDef def = DiplomacyTraits.getTrait(traitId);
                if (def == null) continue;
                LabelAPI label = info.addPara("[" + def.name + "] " + def.desc, 3f);
                label.setHighlight(def.name);
                label.setHighlightColor(def.color);
            }
        }
        info.addSectionHeading("RECENT VOTES", Alignment.MID, 8f);
        info.addPara("No internal votes recorded yet.",
                Misc.getGrayColor(), 4f);
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
                    String text = e.getKey().displayName + " - " + e.getKey().description;
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
            List<FactionBeliefs.BeliefEntry> beliefRows =
                    new ArrayList<FactionBeliefs.BeliefEntry>(beliefs.getEntries());
            Collections.sort(beliefRows, new Comparator<FactionBeliefs.BeliefEntry>() {
                public int compare(FactionBeliefs.BeliefEntry a, FactionBeliefs.BeliefEntry b) {
                    return Integer.compare(b.strength, a.strength);
                }
            });
            for (FactionBeliefs.BeliefEntry e : beliefRows) {
                if (e.visibility == FactionBeliefs.Visibility.SECRET
                        && !access.canSeeSecretBeliefs()) continue;
                BeliefDef def = BeliefRegistry.get(e.beliefId);
                if (def == null) continue;
                String vis = e.visibility == FactionBeliefs.Visibility.SECRET ? " [SECRET]" : "";
                String strength = e.strength == 3 ? "Existential"
                        : e.strength == 2 ? "Important" : "Minor";
                LabelAPI l = info.addPara(def.name + vis + " - " + strength, 2f);
                l.setHighlight(def.name);
                Color nameColor = e.strength >= 3 ? Misc.getNegativeHighlightColor()
                        : e.strength == 2 ? Misc.getHighlightColor() : Misc.getGrayColor();
                l.setHighlightColor(nameColor);
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
                int barLen = Math.min(20, Math.round(w * 3));
                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < barLen; i++) bar.append('#');
                for (int i = barLen; i < 20; i++) bar.append('.');
                LabelAPI l = info.addPara(t.displayName + "  " + bar + "  "
                        + String.format("%.1f", w), 2f);
                l.setHighlight(t.displayName);
                l.setHighlightColor(t.color);
            }
        }
    }

    private void addStrategicTab(TooltipMakerAPI info, FactionAPI f, float widthUnused) {
        if (f.isPlayerFaction()) {
            info.addPara("Strategic AI view is for NPC factions.", Misc.getGrayColor(), 6f);
            return;
        }
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) {
            info.addPara("Manager unavailable.", Misc.getGrayColor(), 6f);
            return;
        }
        String playerId = Global.getSector().getPlayerFaction().getId();
        MemoryVisibility access = MemoryVisibility.getAccessLevel(playerId, f.getId());

        if (!access.canSeeStrategicBasic()) {
            info.addPara("Intelligence insufficient.", Misc.getGrayColor(), 6f);
            return;
        }

        info.addSectionHeading("ACTIVE GOALS", Alignment.MID, 6f);
        StrategicGoalManager gm = mgr.getGoalManager(f.getId());
        if (gm == null || gm.getActiveGoals().isEmpty()) {
            info.addPara("No active goals tracked.", Misc.getGrayColor(), 3f);
        } else if (!access.canSeeStrategicDetailed()) {
            StrategicGoal top = gm.getActiveGoals().get(0);
            String line = "Observed posture: " + top.type.displayName;
            if (top.targetFactionId != null) {
                line += " (pressure toward " + formatFactionName(top.targetFactionId) + ")";
            }
            info.addPara(line, 3f);
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
                archetype.isEmpty() ? "-" : archetype);

        if (access.canSeeStrategicDeep()) {
            CommitmentLedger led = mgr.getGrandStrategy().getLedger(f.getId());
            info.addPara("Archetype commitment (internal):", Misc.getGrayColor(), 4f);
            for (Archetype a : Archetype.values()) {
                if (a == Archetype.OPPORTUNIST) continue;
                float sc = led.getScore(a);
                if (sc < 1f) continue;
                info.addPara(String.format("  %s: %.0f", a.displayName, sc), 1f);
            }
        }
    }

    private void addMemoryTab(TooltipMakerAPI info, FactionAPI f) {
        if (f.isPlayerFaction()) {
            info.addPara("Memory dossier is for NPC factions.", Misc.getGrayColor(), 6f);
            return;
        }
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

    private void addDiplomacyTab(TooltipMakerAPI info, FactionAPI f, float width) {
        float opad = 8f;
        String playerId = Global.getSector().getPlayerFaction().getId();
        info.addSectionHeading("YOUR AGREEMENTS",
                Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, opad);
        if (f.isPlayerFaction()) {
            AgreementIntelEmbed.render(info, width, opad, playerId, null);
            return;
        }
        info.addPara("Agreements between you and " + f.getDisplayName() + ".",
                Misc.getGrayColor(), 4f);
        AgreementIntelEmbed.render(info, width, opad, playerId, f.getId());
        info.addSpacer(8f);
        info.addPara("To propose new deals, use Open Negotiation on the Overview tab.",
                Misc.getGrayColor(), 4f);
    }

    private void addRelationsTab(TooltipMakerAPI info, FactionAPI f, float width) {
        if (f.isPlayerFaction()) {
            info.addPara("Faction-vs-faction relations map: select an NPC faction in the list.",
                    Misc.getGrayColor(), 6f);
            return;
        }
        info.addSectionHeading("RELATIONS WITH OTHERS", Alignment.MID, 6f);
        List<FactionAPI> others = getBrowsableFactions();
        float cardW = Math.max(120f, width - 12f);
        for (FactionAPI o : others) {
            if (o.getId().equals(f.getId())) continue;
            float r = f.getRelationship(o.getId());
            String rStr = String.format("%+.0f", r);
            Color relCol = r >= 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();

            TooltipMakerAPI card = info.beginImageWithText(o.getCrest(), 48f);
            LabelAPI head = card.addPara(o.getDisplayName() + "   " + rStr, 3f);
            head.setHighlight(rStr);
            head.setHighlightColor(relCol);
            card.addPara(repBarAscii(r), relCol, 2f);
            info.addImageWithText(4f);

            info.addButton(" ", BUTTON_FACTION_PREFIX + o.getId(),
                    o.getBaseUIColor(), o.getDarkUIColor(),
                    Alignment.MID, CutStyle.ALL, cardW, 52f, 4f);
        }
    }

    private static String repBarAscii(float relationship) {
        float t = (relationship + 100f) / 200f;
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        int filled = Math.round(t * 20f);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filled; i++) sb.append('#');
        for (int i = filled; i < 20; i++) sb.append('.');
        return sb.toString();
    }

    /**
     * @param intelUi             non-null when opened from {@link FactionBrowserIntel}
     * @param intelItem           intel row that owns this model (for UI refresh)
     * @param refreshCommandTab   run after state change when hosted in {@link FactionsTabPlugin}
     */
    public void handleButton(Object buttonId, IntelUIAPI intelUi, IntelInfoPlugin intelItem,
                             Runnable refreshCommandTab) {
        if (buttonId instanceof String) {
            String id = (String) buttonId;
            if (id.startsWith(BUTTON_FACTION_PREFIX)) {
                selectedFactionId = id.substring(BUTTON_FACTION_PREFIX.length());
                selectedTab = Tab.OVERVIEW;
                refreshUi(intelUi, intelItem, refreshCommandTab);
                return;
            }
            if (id.startsWith(BUTTON_TAB_PREFIX)) {
                try {
                    selectedTab = Tab.valueOf(id.substring(BUTTON_TAB_PREFIX.length()));
                } catch (IllegalArgumentException ignore) {}
                refreshUi(intelUi, intelItem, refreshCommandTab);
                return;
            }
            if (id.startsWith(BUTTON_CONTACT_LEADER_PREFIX)) {
                final String fid = id.substring(BUTTON_CONTACT_LEADER_PREFIX.length());
                if (LeaderAccessGate.isOpen(fid)) {
                    if (intelUi != null) {
                        Nex4xDeferredUi.runNextFrame(new Runnable() {
                            public void run() {
                                NegotiationPanel.openScaled(fid, false);
                            }
                        });
                    } else {
                        NegotiationPanel.openScaled(fid, false);
                    }
                } else {
                    final MarketAPI m = FactionMarketUtil.firstMarketOfFaction(fid);
                    if (m != null) {
                        final InteractionDialogPlugin plugin = new ViceroyDialog(m);
                        final com.fs.starfarer.api.campaign.SectorEntityToken token =
                                m.getPrimaryEntity();
                        if (intelUi != null) {
                            intelUi.showDialog(token, plugin);
                        } else {
                            Nex4xDeferredUi.runNextFrame(new Runnable() {
                                public void run() {
                                    Global.getSector().getCampaignUI().showInteractionDialog(
                                            plugin, token);
                                }
                            });
                        }
                    }
                }
                return;
            }
        }
        if (BUTTON_NEGOTIATE == buttonId && selectedFactionId != null) {
            boolean viceroy = !LeaderAccessGate.isOpen(selectedFactionId);
            if (intelUi != null) {
                final String fid = selectedFactionId;
                final boolean v = viceroy;
                Nex4xDeferredUi.runNextFrame(new Runnable() {
                    public void run() {
                        NegotiationPanel.openScaled(fid, v);
                    }
                });
            } else {
                NegotiationPanel.openScaled(selectedFactionId, viceroy);
            }
        }
    }

    private static void refreshUi(IntelUIAPI intelUi, IntelInfoPlugin intelItem,
                                    Runnable refreshCommandTab) {
        if (intelUi != null && intelItem != null) {
            intelUi.updateUIForItem(intelItem);
        } else if (refreshCommandTab != null) {
            refreshCommandTab.run();
        }
    }
}
