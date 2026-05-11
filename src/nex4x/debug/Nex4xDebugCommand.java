package nex4x.debug;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import nex4x.leaders.LeaderProfile;
import nex4x.leaders.LeaderRegistry;
import nex4x.managers.Nex4xManager;

/** runcode entry points for manual smoke testing from the Console Commands mod. */
public class Nex4xDebugCommand {

    /** runcode nex4x.debug.Nex4xDebugCommand.printLeaders() */
    public static String printLeaders() {
        StringBuilder sb = new StringBuilder();
        LeaderRegistry reg = Nex4xManager.getOrCreateManager().getLeaderRegistry();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.isNeutralFaction() || f.isPlayerFaction()) continue;
            LeaderProfile p = reg.getProfile(f.getId());
            sb.append(f.getId())
              .append(" → ").append(p.displayName())
              .append(" [").append(p.getPersonality()).append("]")
              .append("  title=").append(p.titleString())
              .append("\n");
        }
        Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.speakGreeting("hegemony"); */
    public static String speakGreeting(String factionId) {
        nex4x.leaders.LeaderProfile p = nex4x.managers.Nex4xManager
                .getOrCreateManager().getLeaderRegistry().getProfile(factionId);
        com.fs.starfarer.api.campaign.FactionAPI player =
                com.fs.starfarer.api.Global.getSector().getPlayerFaction();
        float rel = player.getRelationship(factionId);
        nex4x.leaders.ReputationTier tier = nex4x.leaders.ReputationTier.fromRawRelation(rel);
        java.util.Map<String,String> ctx = new java.util.HashMap<String,String>();
        ctx.put("player", player.getDisplayName());
        ctx.put("leader", p.displayName());
        ctx.put("faction", com.fs.starfarer.api.Global.getSector().getFaction(factionId).getDisplayName());
        String line = nex4x.leaders.DialogueSystem.get().resolve(
                p, nex4x.leaders.Situation.GREETING, tier, ctx);
        String msg = "[" + factionId + "] " + p.displayName() + " [" + p.getPersonality() + ", " + tier + "]: " + line;
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(msg);
        return msg;
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.openViceroy("hegemony"); */
    public static String openViceroy(String factionId) {
        com.fs.starfarer.api.campaign.econ.MarketAPI m = firstMarketOfFaction(factionId);
        if (m == null) return "No market found for " + factionId;
        com.fs.starfarer.api.Global.getSector().getCampaignUI().showInteractionDialog(
                new nex4x.ui.ViceroyDialog(m), m.getPrimaryEntity());
        return "Opened viceroy dialog at " + m.getName();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.openLeader("hegemony"); */
    public static String openLeader(String factionId) {
        nex4x.leaders.LeaderAccessGate.Gate g = nex4x.leaders.LeaderAccessGate.resolve(factionId);
        if (g == nex4x.leaders.LeaderAccessGate.Gate.NONE) {
            return "Leader audience is gated. No path open. (rapport/commission/own-colony/other-means)";
        }
        nex4x.ui.NegotiationPanel.openScaled(factionId, false);
        return "Opened negotiation panel for " + factionId + " via gate " + g;
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.declareFriendship("hegemony"); */
    public static String declareFriendship(String target) {
        nex4x.declarations.Declaration d = nex4x.managers.Nex4xManager.getOrCreateManager()
                .getDeclarationManager()
                .declareFriendship(com.fs.starfarer.api.Global.getSector().getPlayerFaction().getId(), target);
        return "Friendship declared with " + target + " (expires day " + d.getExpiryDay() + ")";
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.denounce("pirates"); */
    public static String denounce(String target) {
        nex4x.declarations.Declaration d = nex4x.managers.Nex4xManager.getOrCreateManager()
                .getDeclarationManager()
                .declareDenouncement(com.fs.starfarer.api.Global.getSector().getPlayerFaction().getId(), target);
        return "Denounced " + target + " (expires day " + d.getExpiryDay() + ", CB unlocks at 3 mo)";
    }

    static com.fs.starfarer.api.campaign.econ.MarketAPI firstMarketOfFaction(String factionId) {
        return nex4x.util.FactionMarketUtil.firstMarketOfFaction(factionId);
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.smokeTest(); */
    public static String smokeTest() {
        StringBuilder sb = new StringBuilder("[Nex4x v5 smoke test]\n");
        // 1. Every tier-1 faction has a profile
        String[] t1 = {"hegemony", "tritachyon", "sindrian_diktat", "luddic_church",
                       "luddic_path", "persean", "independent", "pirates", "remnants"};
        nex4x.managers.Nex4xManager mgr = nex4x.managers.Nex4xManager.getOrCreateManager();
        for (String fid : t1) {
            nex4x.leaders.LeaderProfile p = mgr.getLeaderRegistry().getProfile(fid);
            sb.append("  ").append(fid).append(" -> ").append(p.displayName())
              .append(" [").append(p.getPersonality()).append("]\n");
        }
        // 2. Dialogue system resolves without error
        nex4x.leaders.LeaderProfile heg = mgr.getLeaderRegistry().getProfile("hegemony");
        java.util.Map<String,String> ctx = new java.util.HashMap<String,String>();
        ctx.put("player", "Commander");
        ctx.put("leader", heg.displayName());
        ctx.put("faction", "Hegemony");
        String line = nex4x.leaders.DialogueSystem.get().resolve(
                heg, nex4x.leaders.Situation.GREETING, nex4x.leaders.ReputationTier.NEUTRAL, ctx);
        sb.append("  dialogue: ").append(line).append("\n");
        // 3. Declaration config loaded
        sb.append("  friendship.durationDays = ")
          .append(nex4x.declarations.DeclarationConfig.get(
                  nex4x.declarations.DeclarationType.FRIENDSHIP).durationDays).append("\n");
        // 4. Valuator + Balance calc compile-path sanity
        nex4x.negotiation.DealProposal deal = new nex4x.negotiation.DealProposal(
                com.fs.starfarer.api.Global.getSector().getPlayerFaction().getId(), "hegemony");
        nex4x.negotiation.BalanceCalculator.Result r =
                nex4x.negotiation.BalanceCalculator.evaluate(deal, heg, 500);
        sb.append("  empty-deal balance = ").append(r.balance)
          .append(" verdict=").append(r.verdict).append("\n");
        // 5. Intel tier
        sb.append("  hegemony intel tier = ")
          .append(nex4x.leaders.IntelTierResolver.resolve("hegemony")).append("\n");

        sb.append("[OK]");
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.auditAgents() — prints nex4x companion data + Nex spec mapping. */
    public static String auditAgents() {
        StringBuilder sb = new StringBuilder("[nex4x audit-agents]\n");
        nex4x.agents.Nex4xAgentManager mgr = nex4x.agents.Nex4xAgentManager.get();
        if (mgr == null) { sb.append("  (no manager)\n"); return sb.toString(); }
        // Registered defs
        try {
            int defs = 0;
            for (String id : new String[]{
                    nex4x.agents.actions.ActionDefIds.DEEP_COVER,
                    nex4x.agents.actions.ActionDefIds.DIPLOMAT_OFFICIAL,
                    nex4x.agents.actions.ActionDefIds.DIPLOMAT_LEAK,
                    nex4x.agents.actions.ActionDefIds.GUERRILLA_BUILD_NETWORK,
                    nex4x.agents.actions.ActionDefIds.GUERRILLA_FALSE_FLAG,
                    nex4x.agents.actions.ActionDefIds.GUERRILLA_HIRE_MERCS,
                    nex4x.agents.actions.ActionDefIds.GUERRILLA_INCITE_RAID}) {
                if (exerelin.campaign.CovertOpsManager.actionDefsById.containsKey(id)) defs++;
            }
            sb.append("  Registered nex4x defs in CovertOpsManager: ")
              .append(defs).append("/7\n");
        } catch (Throwable t) {
            sb.append("  Nex CovertOpsManager not available: ").append(t.getMessage()).append("\n");
        }
        // Companion data
        sb.append("  Tracked agents: ").append(mgr.getAll().size()).append("\n");
        for (java.util.Map.Entry<String, nex4x.agents.Nex4xAgentData> e : mgr.getAll().entrySet()) {
            nex4x.agents.Nex4xAgentData d = e.getValue();
            sb.append("    ").append(e.getKey())
              .append(" type=").append(d.getType())
              .append(" owner=").append(d.getOwnerFactionId())
              .append(" lvl=").append(d.getBuildupTracker().getLevel())
              .append("\n");
        }
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.auditDiplomacy() — verifies Nex DiplomacyManager + AllianceManager integration. */
    public static String auditDiplomacy() {
        StringBuilder sb = new StringBuilder("[nex4x audit-diplomacy]\n");
        // 1. Count DiplomacyIntel events visible in IntelManager
        try {
            java.util.List<com.fs.starfarer.api.campaign.comm.IntelInfoPlugin> events =
                    com.fs.starfarer.api.Global.getSector().getIntelManager().getIntel(
                            exerelin.campaign.intel.diplomacy.DiplomacyIntel.class);
            sb.append("  DiplomacyIntel events: ")
              .append(events == null ? 0 : events.size()).append("\n");
        } catch (Throwable t) {
            sb.append("  DiplomacyIntel lookup failed: ").append(t.getMessage()).append("\n");
        }
        // 2. For each active COALITION agreement, check whether a Nex Alliance shadow exists.
        try {
            nex4x.managers.Nex4xManager mgr = nex4x.managers.Nex4xManager.getManager();
            if (mgr == null) {
                sb.append("  Nex4xManager: not initialized\n");
            } else {
                int total = 0, shadowed = 0;
                for (nex4x.agreements.Agreement a : mgr.getAgreementManager().getAllAgreements()) {
                    if (!a.isActive()) continue;
                    if (a.getType() != nex4x.agreements.AgreementType.COALITION) continue;
                    total++;
                    exerelin.campaign.alliances.Alliance alA =
                            exerelin.campaign.AllianceManager.getFactionAlliance(a.getFactionIdA());
                    exerelin.campaign.alliances.Alliance alB =
                            exerelin.campaign.AllianceManager.getFactionAlliance(a.getFactionIdB());
                    if (alA != null && alA == alB) shadowed++;
                    sb.append("  COALITION ").append(a.getFactionIdA()).append("<->")
                      .append(a.getFactionIdB())
                      .append(" alliance=")
                      .append(alA != null ? alA.getName() : "<none>").append("\n");
                }
                sb.append("  Coalition shadow coverage: ").append(shadowed)
                  .append("/").append(total).append("\n");
            }
        } catch (Throwable t) {
            sb.append("  Coalition shadow audit failed: ").append(t.getMessage()).append("\n");
        }
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.auditInit() — prints manager init state. */
    public static String auditInit() {
        StringBuilder sb = new StringBuilder("[nex4x audit-init]\n");
        nex4x.managers.Nex4xManager mgr = nex4x.managers.Nex4xManager.getManager();
        sb.append("  Nex4xManager = ").append(mgr == null ? "null" : "ok").append("\n");
        if (mgr != null) {
            sb.append("  agreementManager = ").append(mgr.getAgreementManager() != null).append("\n");
            sb.append("  declarationManager = ").append(mgr.getDeclarationManager() != null).append("\n");
            sb.append("  leaderRegistry = ").append(mgr.getLeaderRegistry() != null).append("\n");
            sb.append("  casusBelliManager = ").append(mgr.getCasusBelliManager() != null).append("\n");
        }
        sb.append("  FactionPowerRankings cache size = (rebuild on read)\n");
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.auditClock() — prints time-unit diagnostics. */
    public static String auditClock() {
        StringBuilder sb = new StringBuilder("[nex4x audit-clock]\n");
        long ts = nex4x.util.Nex4xClock.now();
        float absDay = nex4x.util.Nex4xClock.currentAbsoluteDay();
        sb.append("  now timestamp = ").append(ts).append("\n");
        sb.append("  current absolute day = ").append(absDay).append("\n");
        sb.append("  isLegacyDayValue(ts) = ").append(nex4x.util.Nex4xClock.isLegacyDayValue(ts)).append("\n");
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.auditNexSync() — CB coverage, coalition shadow, floor compliance, and sweepDiplomacyBrains. */
    public static String auditNexSync() {
        StringBuilder sb = new StringBuilder("[nex4x audit-nex-sync]\n");
        nex4x.managers.Nex4xManager mgr = nex4x.managers.Nex4xManager.getManager();
        if (mgr == null) {
            sb.append("  Nex4xManager: not initialized\n");
            com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
            return sb.toString();
        }

        // 1. Active wars + CB coverage
        try {
            int cbCovered = 0, cbAbsent = 0;
            java.util.List<com.fs.starfarer.api.campaign.FactionAPI> factions =
                    com.fs.starfarer.api.Global.getSector().getAllFactions();
            for (com.fs.starfarer.api.campaign.FactionAPI a : factions) {
                if (a.isNeutralFaction()) continue;
                for (com.fs.starfarer.api.campaign.FactionAPI b : factions) {
                    if (b == a || b.isNeutralFaction()) continue;
                    if (!a.isHostileTo(b)) continue;
                    nex4x.casusbelli.CasusBelli cb =
                            mgr.getCasusBelliManager().getActiveCasusBelliFor(a.getId(), b.getId());
                    if (cb != null) cbCovered++; else cbAbsent++;
                }
            }
            sb.append("  [1] Active wars CB coverage: covered=").append(cbCovered)
              .append(" absent=").append(cbAbsent).append("\n");
        } catch (Throwable t) {
            sb.append("  [1] CB coverage audit failed: ").append(t.getMessage()).append("\n");
        }

        // 2. Coalition shadow coverage
        try {
            int total = 0, shadowed = 0;
            for (nex4x.agreements.Agreement a : mgr.getAgreementManager().getAllAgreements()) {
                if (!a.isActive()) continue;
                if (a.getType() != nex4x.agreements.AgreementType.COALITION) continue;
                total++;
                exerelin.campaign.alliances.Alliance al =
                        exerelin.campaign.AllianceManager.getFactionAlliance(a.getFactionIdA());
                if (al != null) shadowed++;
                sb.append("  COALITION ").append(a.getFactionIdA()).append("<->")
                  .append(a.getFactionIdB())
                  .append(" alliance=").append(al != null ? al.getName() : "<none>").append("\n");
            }
            sb.append("  [2] Coalition shadow: shadowed=").append(shadowed)
              .append(" unshadowed=").append(total - shadowed).append("\n");
        } catch (Throwable t) {
            sb.append("  [2] Coalition shadow audit failed: ").append(t.getMessage()).append("\n");
        }

        // 3. NAP/DefPact/MilPartner/EconPartner floor compliance
        try {
            int met = 0, belowFloor = 0;
            java.util.Map<nex4x.agreements.AgreementType, Float> floors =
                    new java.util.EnumMap<>(nex4x.agreements.AgreementType.class);
            floors.put(nex4x.agreements.AgreementType.NAP, 0.10f);
            floors.put(nex4x.agreements.AgreementType.DEFENSIVE_PACT, 0.25f);
            floors.put(nex4x.agreements.AgreementType.MILITARY_PARTNERSHIP, 0.50f);
            floors.put(nex4x.agreements.AgreementType.ECONOMIC_PARTNERSHIP, 0.25f);
            for (nex4x.agreements.Agreement a : mgr.getAgreementManager().getAllAgreements()) {
                if (!a.isActive()) continue;
                Float floor = floors.get(a.getType());
                if (floor == null) continue;
                float rel = com.fs.starfarer.api.Global.getSector()
                        .getFaction(a.getFactionIdA()).getRelationship(a.getFactionIdB());
                if (rel >= floor) met++; else belowFloor++;
            }
            sb.append("  [3] Floor compliance: met=").append(met)
              .append(" belowFloor=").append(belowFloor).append("\n");
        } catch (Throwable t) {
            sb.append("  [3] Floor compliance audit failed: ").append(t.getMessage()).append("\n");
        }

        // 4. DiplomacyBrain sweep
        try {
            nex4x.integration.NexDiplomacyBridge.sweepDiplomacyBrains();
            sb.append("  [4] sweepDiplomacyBrains: ran OK\n");
        } catch (Throwable t) {
            sb.append("  [4] sweepDiplomacyBrains failed: ").append(t.getMessage()).append("\n");
        }

        sb.append("[OK]");
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.auditTribute() — walks economy and lists every market carrying TributeCondition. */
    public static String auditTribute() {
        StringBuilder sb = new StringBuilder("[nex4x audit-tribute]\n");
        try {
            int total = 0;
            for (com.fs.starfarer.api.campaign.econ.MarketAPI market :
                    com.fs.starfarer.api.Global.getSector().getEconomy().getMarketsCopy()) {
                if (!market.hasCondition(exerelin.campaign.econ.TributeCondition.CONDITION_ID)) continue;
                total++;
                String receiver = "<unknown>";
                try {
                    com.fs.starfarer.api.campaign.econ.MarketConditionPlugin plugin =
                            market.getSpecificCondition(exerelin.campaign.econ.TributeCondition.CONDITION_ID).getPlugin();
                    if (plugin instanceof exerelin.campaign.econ.TributeCondition) {
                        java.lang.reflect.Field f =
                                exerelin.campaign.econ.TributeCondition.class.getDeclaredField("faction");
                        f.setAccessible(true);
                        com.fs.starfarer.api.campaign.FactionAPI factionApi =
                                (com.fs.starfarer.api.campaign.FactionAPI) f.get(plugin);
                        receiver = (factionApi != null) ? factionApi.getId() : "<null>";
                    }
                } catch (Throwable t2) {
                    receiver = "<reflect-err: " + t2.getMessage() + ">";
                }
                sb.append("  ").append(market.getId())
                  .append(" | owner=").append(market.getFactionId())
                  .append(" | -> ").append(receiver).append("\n");
            }
            sb.append("  Total tributed markets: ").append(total).append("\n");
        } catch (Throwable t) {
            sb.append("  auditTribute failed: ").append(t.getMessage()).append("\n");
        }
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.openPeaceConference("hegemony","sindrian_diktat"); */
    public static String openPeaceConference(String attackerId, String defenderId) {
        nex4x.peace.PeaceConference pc = new nex4x.peace.PeaceConference(attackerId, defenderId);
        nex4x.ui.PeaceConferenceDialog.openScaled(pc);
        return "Opened peace conference: " + attackerId + " vs " + defenderId;
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.auditRels() — prints relation diagnostics for all live factions vs player. */
    public static String auditRels() {
        StringBuilder sb = new StringBuilder("[nex4x audit-rels]\n");
        FactionAPI player = com.fs.starfarer.api.Global.getSector().getPlayerFaction();
        for (FactionAPI f : com.fs.starfarer.api.Global.getSector().getAllFactions()) {
            if (f == null || f.isNeutralFaction() || f == player) continue;
            float raw = player.getRelationship(f.getId());
            int pct = nex4x.util.Nex4xRelations.toPercentInt(raw);
            com.fs.starfarer.api.campaign.RepLevel lvl = nex4x.util.Nex4xRelations.repLevel(raw);
            nex4x.leaders.ReputationTier tier = nex4x.leaders.ReputationTier.fromRawRelation(raw);
            sb.append("  ").append(f.getId())
              .append("  raw=").append(String.format("%+.2f", raw))
              .append("  pct=").append(String.format("%+d", pct))
              .append("  repLevel=").append(lvl)
              .append("  tier=").append(tier)
              .append("\n");
        }
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }
}
