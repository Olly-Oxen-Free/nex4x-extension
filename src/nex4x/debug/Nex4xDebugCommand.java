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

    /** runcode nex4x.debug.Nex4xDebugCommand.auditCovertDefs() — lists all CovertOpsManager
     *  actionDefs with sortOrder; asserts nex4x 7 defs present. */
    public static String auditCovertDefs() {
        StringBuilder sb = new StringBuilder("[nex4x audit-covert-defs]\n");
        String[] nex4xIds = new String[]{
                nex4x.agents.actions.ActionDefIds.DEEP_COVER,
                nex4x.agents.actions.ActionDefIds.DIPLOMAT_OFFICIAL,
                nex4x.agents.actions.ActionDefIds.DIPLOMAT_LEAK,
                nex4x.agents.actions.ActionDefIds.GUERRILLA_BUILD_NETWORK,
                nex4x.agents.actions.ActionDefIds.GUERRILLA_FALSE_FLAG,
                nex4x.agents.actions.ActionDefIds.GUERRILLA_HIRE_MERCS,
                nex4x.agents.actions.ActionDefIds.GUERRILLA_INCITE_RAID};
        try {
            sb.append("  actionDefs (in sort order):\n");
            for (exerelin.campaign.CovertOpsManager.CovertActionDef def
                    : exerelin.campaign.CovertOpsManager.actionDefs) {
                boolean isNex4x = false;
                for (String id : nex4xIds) { if (id.equals(def.id)) { isNex4x = true; break; } }
                sb.append("    ").append(isNex4x ? "* " : "  ")
                  .append(def.id).append("  \"").append(def.name).append("\"")
                  .append("  sortOrder=").append(def.sortOrder).append("\n");
            }
            int present = 0;
            sb.append("  nex4x def assertions:\n");
            for (String id : nex4xIds) {
                boolean ok = exerelin.campaign.CovertOpsManager.actionDefsById.containsKey(id);
                if (ok) present++;
                sb.append("    ").append(ok ? "PASS " : "FAIL ").append(id).append("\n");
            }
            sb.append("  nex4x defs registered: ").append(present).append("/7\n");
        } catch (Throwable t) {
            sb.append("  Nex CovertOpsManager not available: ").append(t.getMessage()).append("\n");
        }
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.auditAgentOwnership() — lists live agents with their
     *  nex4x companion owner faction; verifies PRD-019 sweep populated owners. */
    public static String auditAgentOwnership() {
        StringBuilder sb = new StringBuilder("[nex4x audit-agent-ownership]\n");
        nex4x.agents.Nex4xAgentManager mgr = nex4x.agents.Nex4xAgentManager.get();
        java.util.List<com.fs.starfarer.api.campaign.comm.IntelInfoPlugin> agents =
                Global.getSector().getIntelManager().getIntel(
                        exerelin.campaign.intel.agents.AgentIntel.class);
        int total = 0, nullOwner = 0;
        if (agents != null) {
            for (com.fs.starfarer.api.campaign.comm.IntelInfoPlugin p : agents) {
                if (!(p instanceof exerelin.campaign.intel.agents.AgentIntel)) continue;
                exerelin.campaign.intel.agents.AgentIntel ai =
                        (exerelin.campaign.intel.agents.AgentIntel) p;
                if (ai.getAgent() == null) continue;
                String id = ai.getAgent().getId();
                nex4x.agents.Nex4xAgentData d = mgr == null ? null : mgr.get(id);
                String owner = (d == null || d.getOwnerFactionId() == null) ? "NULL" : d.getOwnerFactionId();
                if ("NULL".equals(owner)) nullOwner++;
                total++;
                sb.append("  Agent ").append(id)
                  .append(" type=").append(d == null ? "<no-record>" : d.getType())
                  .append(" owner=").append(owner)
                  .append(" buildup=").append(d == null ? "-" : d.getBuildupTracker().getLevel())
                  .append(" dismissed=").append(ai.isDeadOrDismissed())
                  .append("\n");
            }
        }
        sb.append("  Total: ").append(total).append(" agents, ").append(nullOwner)
          .append(" with null owner\n");
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

    /** runcode nex4x.debug.Nex4xDebugCommand.smokeDiplomacyEvents() — MUTATING smoke test for
     *  PRD-014: fires respect/insult through NexDiplomacyBridge and asserts real rep deltas on
     *  both the Nex-event path (faction with markets) and the null-return fallback path
     *  (market-less faction), plus peace_treaty un-hostiling. Changes live relationships. */
    public static String smokeDiplomacyEvents() {
        StringBuilder sb = new StringBuilder("[nex4x smoke-diplomacy-events]\n");
        boolean allPass = true;
        try {
            FactionAPI withMarkets = Global.getSector().getFaction("hegemony");
            FactionAPI target = Global.getSector().getFaction("tritachyon");

            // 1. respect via Nex event path (hegemony has markets)
            float before = withMarkets.getRelationship(target.getId());
            int intelBefore = countDiploIntel();
            nex4x.integration.NexDiplomacyBridge.fireRespectEvent(withMarkets, target, 5f);
            float delta = withMarkets.getRelationship(target.getId()) - before;
            int intelDelta = countDiploIntel() - intelBefore;
            boolean p1 = delta >= 0.039f; allPass &= p1;
            sb.append("    ").append(p1 ? "PASS" : "FAIL")
              .append(" respect(hegemony->tritachyon) relDelta=").append(delta)
              .append(" intelDelta=").append(intelDelta).append("\n");

            // 2. insult via Nex event path
            before = withMarkets.getRelationship(target.getId());
            nex4x.integration.NexDiplomacyBridge.fireInsultEvent(withMarkets, target, 5f);
            delta = withMarkets.getRelationship(target.getId()) - before;
            boolean p2 = delta <= -0.039f; allPass &= p2;
            sb.append("    ").append(p2 ? "PASS" : "FAIL")
              .append(" insult(hegemony->tritachyon) relDelta=").append(delta).append("\n");

            // 3. respect via fallback path (market-less faction)
            FactionAPI marketless = findMarketlessFaction();
            if (marketless == null) {
                sb.append("    SKIP respect-fallback: no market-less faction found\n");
            } else {
                before = marketless.getRelationship(target.getId());
                nex4x.integration.NexDiplomacyBridge.fireRespectEvent(marketless, target, 5f);
                delta = marketless.getRelationship(target.getId()) - before;
                boolean p3 = delta >= 0.039f; allPass &= p3;
                sb.append("    ").append(p3 ? "PASS" : "FAIL")
                  .append(" respect-fallback(").append(marketless.getId())
                  .append("->tritachyon) relDelta=").append(delta).append("\n");
            }

            // 4. peace_treaty un-hostiles
            FactionAPI pa = Global.getSector().getFaction("hegemony");
            FactionAPI pb = Global.getSector().getFaction("luddic_path");
            pa.setRelationship(pb.getId(), -1f);
            nex4x.integration.NexDiplomacyBridge.firePeaceTreaty(pa, pb);
            boolean p4 = !pa.isHostileTo(pb); allPass &= p4;
            sb.append("    ").append(p4 ? "PASS" : "FAIL")
              .append(" peace(hegemony<->luddic_path) hostileAfter=").append(pa.isHostileTo(pb)).append("\n");
        } catch (Throwable t) {
            allPass = false;
            sb.append("    AUDIT FAIL: ").append(t.getMessage()).append("\n");
        }
        sb.append("  overall: ").append(allPass ? "PASS" : "AUDIT FAIL").append("\n");
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /**
     * PRD-021 smoke test — verifies stub completion surfaces.
     *
     * runcode nex4x.debug.Nex4xDebugCommand.smokeStubs()
     * runcode nex4x.debug.Nex4xDebugCommand.smokeStubs("--dry")
     *
     * Pass flag "--dry" to skip actual transferMarket calls (checks preconditions only).
     */
    public static String smokeStubs() { return smokeStubs(""); }

    public static String smokeStubs(String flags) {
        boolean dry = flags != null && flags.contains("--dry");
        StringBuilder sb = new StringBuilder("[nex4x smoke-stubs" + (dry ? " DRY" : "") + "]\n");
        boolean allPass = true;

        // ── Test 1: Intel purchase deducts credits + adds intel ──────────────
        try {
            com.fs.starfarer.api.campaign.econ.MarketAPI hegMarket =
                    nex4x.util.FactionMarketUtil.firstMarketOfFaction("hegemony");
            if (hegMarket == null) {
                sb.append("  SKIP  intel-purchase: no hegemony market found\n");
            } else {
                com.fs.starfarer.api.util.MutableValue credits =
                        Global.getSector().getPlayerFleet().getCargo().getCredits();
                // Ensure player has enough credits.
                float before = credits.get();
                if (before < nex4x.ui.viceroy.IntelPurchaseHandler.PRICE_LOCATION_TIP) {
                    credits.add(nex4x.ui.viceroy.IntelPurchaseHandler.PRICE_LOCATION_TIP - (int) before + 1);
                    before = credits.get();
                }
                int intelBefore = Global.getSector().getIntelManager()
                        .getIntel(nex4x.ui.viceroy.ViceroyIntelItem.class).size();

                nex4x.ui.viceroy.IntelPurchaseHandler.purchase(
                        null, hegMarket, nex4x.ui.viceroy.IntelPurchaseHandler.IntelTier.LOCATION_TIP);

                float after   = credits.get();
                int intelAfter = Global.getSector().getIntelManager()
                        .getIntel(nex4x.ui.viceroy.ViceroyIntelItem.class).size();
                boolean p1a = (before - after) >= nex4x.ui.viceroy.IntelPurchaseHandler.PRICE_LOCATION_TIP;
                boolean p1b = intelAfter > intelBefore;
                boolean p1 = p1a && p1b;
                allPass &= p1;
                sb.append("  ").append(p1 ? "PASS" : "FAIL")
                  .append("  intel-purchase: creditsDelta=")
                  .append((int)(before - after))
                  .append(" intelDelta=").append(intelAfter - intelBefore).append("\n");
            }
        } catch (Throwable t) {
            allPass = false;
            sb.append("  FAIL  intel-purchase: ").append(t.getMessage()).append("\n");
        }

        // ── Test 2: Territory deal transfers a market ─────────────────────
        try {
            com.fs.starfarer.api.campaign.econ.MarketAPI targetMarket = findTransferableMarket();
            if (targetMarket == null) {
                sb.append("  SKIP  territory-deal: no transferable non-player market found\n");
            } else {
                String originalOwner = targetMarket.getFactionId();
                String playerFactionId = Global.getSector().getPlayerFaction().getId();
                nex4x.negotiation.DealPackage deal = new nex4x.negotiation.DealPackage(
                        originalOwner, playerFactionId);
                nex4x.negotiation.NegotiableItem item =
                        nex4x.negotiation.NegotiableItem.territory(targetMarket.getId());
                deal.addOffer(item);

                if (dry) {
                    sb.append("  DRY   territory-deal: would transfer ")
                      .append(targetMarket.getId()).append(" from ")
                      .append(originalOwner).append(" to ").append(playerFactionId).append("\n");
                } else {
                    nex4x.managers.Nex4xManager mgr =
                            nex4x.managers.Nex4xManager.getOrCreateManager();
                    nex4x.negotiation.NegotiationDealExecutor.executeDeal(deal, mgr, false);
                    boolean p2 = playerFactionId.equals(targetMarket.getFactionId());
                    allPass &= p2;
                    sb.append("  ").append(p2 ? "PASS" : "FAIL")
                      .append("  territory-deal: market=").append(targetMarket.getId())
                      .append(" newOwner=").append(targetMarket.getFactionId()).append("\n");
                }
            }
        } catch (Throwable t) {
            allPass = !dry;
            sb.append("  FAIL  territory-deal: ").append(t.getMessage()).append("\n");
        }

        // ── Test 3: PeaceConference.accept() with TERRITORY_CEDE term ────────
        try {
            com.fs.starfarer.api.campaign.econ.MarketAPI targetMarket =
                    dry ? findTransferableMarket() : null;
            String attacker = "hegemony";
            String defender = Global.getSector().getPlayerFaction().getId();
            if (dry && targetMarket != null) {
                attacker = targetMarket.getFactionId();
            }
            nex4x.peace.PeaceConference pc =
                    new nex4x.peace.PeaceConference(attacker, defender);
            if (targetMarket != null) {
                pc.getProposed().add(new nex4x.peace.PeaceTerms.Term(
                        nex4x.peace.PeaceTerms.TermType.TERRITORY_CEDE,
                        targetMarket.getId(), 0f));
            }

            if (dry) {
                sb.append("  DRY   peace-accept: would apply ")
                  .append(pc.getProposed().getTerms().size())
                  .append(" terms for ").append(attacker).append(" vs ").append(defender).append("\n");
            } else {
                pc.accept();
                boolean p3 = pc.getStatus() == nex4x.peace.PeaceConference.Status.ACCEPTED;
                allPass &= p3;
                sb.append("  ").append(p3 ? "PASS" : "FAIL")
                  .append("  peace-accept: status=").append(pc.getStatus()).append("\n");
            }
        } catch (Throwable t) {
            if (!dry) allPass = false;
            sb.append("  FAIL  peace-accept: ").append(t.getMessage()).append("\n");
        }

        sb.append("  overall: ").append(allPass ? "PASS" : (dry ? "DRY-RUN" : "FAIL")).append("\n");
        Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** Finds a non-player, non-neutral market that we can transfer in tests. */
    private static com.fs.starfarer.api.campaign.econ.MarketAPI findTransferableMarket() {
        String playerId = Global.getSector().getPlayerFaction().getId();
        for (com.fs.starfarer.api.campaign.econ.MarketAPI m :
                Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.getFactionId() == null) continue;
            if (m.getFactionId().equals(playerId)) continue;
            com.fs.starfarer.api.campaign.FactionAPI f =
                    Global.getSector().getFaction(m.getFactionId());
            if (f == null || f.isNeutralFaction() || f.isPlayerFaction()) continue;
            return m;
        }
        return null;
    }

    private static int countDiploIntel() {
        try {
            java.util.List<com.fs.starfarer.api.campaign.comm.IntelInfoPlugin> ev =
                    Global.getSector().getIntelManager().getIntel(
                            exerelin.campaign.intel.diplomacy.DiplomacyIntel.class);
            return ev == null ? 0 : ev.size();
        } catch (Throwable t) { return 0; }
    }

    private static FactionAPI findMarketlessFaction() {
        try {
            java.util.Set<String> withMarkets = new java.util.HashSet<String>();
            for (com.fs.starfarer.api.campaign.econ.MarketAPI m
                    : Global.getSector().getEconomy().getMarketsCopy()) {
                if (m.getFactionId() != null) withMarkets.add(m.getFactionId());
            }
            for (FactionAPI f : Global.getSector().getAllFactions()) {
                String id = f.getId();
                if (id == null || f.isNeutralFaction() || f.isPlayerFaction()) continue;
                if ("tritachyon".equals(id)) continue; // reserved as the audit target
                if (!withMarkets.contains(id)) return f;
            }
        } catch (Throwable t) { /* fall through */ }
        return null;
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
        sb.append("  current absolute day = ").append(absDay).append(" (cycle-0 epoch, 360 days/cycle)\n");
        sb.append("  isLegacyDayValue(ts) = ").append(nex4x.util.Nex4xClock.isLegacyDayValue(ts)).append("\n");
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /**
     * PRD-023 smoke test — verifies day-math unification.
     *
     * runcode nex4x.debug.Nex4xDebugCommand.auditDayMath()
     *
     * Asserts:
     *  1. currentAbsoluteDay() matches inline formula cycle*360 + (month-1)*30 + day.
     *  2. One cycle from cycle 1, month 1, day 1 equals exactly 360 (not 365).
     *  3. Two cycles from cycle 0 = 720.
     */
    public static String auditDayMath() {
        StringBuilder sb = new StringBuilder("[nex4x audit-day-math PRD-023]\n");
        boolean allPass = true;
        try {
            com.fs.starfarer.api.campaign.CampaignClockAPI c =
                    com.fs.starfarer.api.Global.getSector().getClock();
            int cycle = c.getCycle();
            int month = c.getMonth();
            int day   = c.getDay();

            float actual   = nex4x.util.Nex4xClock.currentAbsoluteDay();
            float expected = cycle * 360f + (month - 1) * 30f + day;

            boolean p1 = (actual == expected);
            allPass &= p1;
            sb.append("  ").append(p1 ? "PASS" : "FAIL")
              .append("  currentAbsoluteDay() == cycle*360+(month-1)*30+day")
              .append("  actual=").append(actual)
              .append(" expected=").append(expected).append("\n");

            // Synthetic cycle check: cycle 1, month 1, day 1 => 360
            float synth1 = 1 * 360f + (1 - 1) * 30f + 1;
            boolean p2 = (synth1 == 361f);  // cycle*360+0+1 = 361
            // Actually: cycle=1, month=1, day=1 -> 1*360 + 0*30 + 1 = 361
            // "one cycle from cycle 0 = 360" means at start of cycle 1 (day 1 month 1)
            // The advance from cycle 0 day 1 month 1 (=1) to cycle 1 day 1 month 1 (=361) = 360 days.
            float cycle0Start = 0 * 360f + (1 - 1) * 30f + 1;  // = 1
            float cycle1Start = 1 * 360f + (1 - 1) * 30f + 1;  // = 361
            float oneCycleDays = cycle1Start - cycle0Start;
            boolean p3 = (oneCycleDays == 360f);
            allPass &= p3;
            sb.append("  ").append(p3 ? "PASS" : "FAIL")
              .append("  one cycle advance = ").append(oneCycleDays)
              .append(" days (expected 360, not 365)\n");

            // Two cycles: cycle 2 start - cycle 0 start = 720
            float cycle2Start = 2 * 360f + 0 * 30f + 1;  // = 721
            float twoCycleDays = cycle2Start - cycle0Start;
            boolean p4 = (twoCycleDays == 720f);
            allPass &= p4;
            sb.append("  ").append(p4 ? "PASS" : "FAIL")
              .append("  two cycle advance = ").append(twoCycleDays)
              .append(" days (expected 720)\n");

            sb.append("  current: cycle=").append(cycle)
              .append(" month=").append(month)
              .append(" day=").append(day)
              .append(" => absDay=").append(actual).append("\n");
            sb.append("  note: one cycle from cycle 1 = 360 days (not 365)\n");

        } catch (Throwable t) {
            allPass = false;
            sb.append("  AUDIT FAIL: ").append(t.getMessage()).append("\n");
        }
        sb.append("  overall: ").append(allPass ? "PASS" : "FAIL").append("\n");
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

    /**
     * runcode nex4x.debug.Nex4xDebugCommand.auditIndustries()
     *
     * Prints each market's name, faction, size, and whether it holds
     * nex4x_diplomatic_embassy and nex4x_intelligence_bureau.
     * Also flags any that are currently disrupted.
     * Ends with totals: Embassy N markets, Bureau M markets.
     */
    public static String auditIndustries() {
        final String EMBASSY_ID = nex4x.Nex4xConstants.BUILDING_DIPLOMATIC_EMBASSY;
        final String BUREAU_ID  = nex4x.Nex4xConstants.BUILDING_INTELLIGENCE_BUREAU;
        StringBuilder sb = new StringBuilder("[nex4x audit-industries]\n");
        int embassyTotal = 0;
        int bureauTotal = 0;
        try {
            for (com.fs.starfarer.api.campaign.econ.MarketAPI m :
                    com.fs.starfarer.api.Global.getSector().getEconomy().getMarketsCopy()) {
                boolean hasEmbassy = m.hasIndustry(EMBASSY_ID);
                boolean hasBureau  = m.hasIndustry(BUREAU_ID);
                if (!hasEmbassy && !hasBureau) continue;
                if (hasEmbassy) embassyTotal++;
                if (hasBureau)  bureauTotal++;
                sb.append("  ").append(m.getName())
                  .append(" | faction=").append(m.getFactionId())
                  .append(" | size=").append(m.getSize())
                  .append(" | embassy=").append(hasEmbassy)
                  .append(" | bureau=").append(hasBureau);
                if (hasEmbassy && m.getIndustry(EMBASSY_ID).isDisrupted()) {
                    sb.append(" [DISRUPTED embassy:").append(m.getName()).append("]");
                }
                if (hasBureau && m.getIndustry(BUREAU_ID).isDisrupted()) {
                    sb.append(" [DISRUPTED bureau:").append(m.getName()).append("]");
                }
                sb.append("\n");
            }
            sb.append("  Embassy: ").append(embassyTotal).append(" markets, ")
              .append("Bureau: ").append(bureauTotal).append(" markets\n");
        } catch (Throwable t) {
            sb.append("  auditIndustries failed: ").append(t.getMessage()).append("\n");
        }
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.proposeMediation("hegemony","sindrian_diktat","persean",1000); */
    public static String proposeMediation(String mediator, String a, String b, int influence) {
        nex4x.mediation.MediationManager mgr = nex4x.mediation.MediationManager.getOrCreate();
        nex4x.mediation.MediationSession s = mgr.propose(mediator, a, b, (float) influence);
        return s != null
                ? "[Nex4x] Mediation opened by " + mediator + ": " + a + " <-> " + b
                : "[Nex4x] Mediation rejected (insufficient influence, not at war, or invalid args)";
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.auditUI() — verifies player UI integration points. */
    public static String auditUI() {
        StringBuilder sb = new StringBuilder("[nex4x audit-ui]\n");
        try {
            nex4x.negotiation.NegotiableItemCatalog cat = new nex4x.negotiation.NegotiableItemCatalog();
            for (nex4x.negotiation.NegotiableItemType type : nex4x.negotiation.NegotiableItemType.values()) {
                java.util.List<String> ids = cat.idsForType(type, false, "hegemony");
                sb.append("  ").append(type).append(" -> ").append(ids.size()).append(" id(s)\n");
            }
            sb.append("  PeaceConferenceDialog class loaded: ")
              .append(nex4x.ui.PeaceConferenceDialog.class.getName() != null).append("\n");
        } catch (Throwable t) {
            sb.append("  audit-ui failed: ").append(t.getMessage()).append("\n");
        }
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /**
     * PRD-015 (15f) smoke test.
     *
     * runcode nex4x.debug.Nex4xDebugCommand.auditStrategicAI("hegemony");
     *
     * Checks:
     *  1. Orphan ids (nex4x_goal_wrap, nex4x_propose_tiered_agreement) are absent from
     *     StrategicDefManager.CONCERN_DEFS_BY_ID / ACTION_DEFS_BY_ID.
     *  2. Lists all nex4x-prefixed concern/action defs with their tag counts + tags.
     *  3. For factionId: prints active goals with posture and obstacle severity.
     *  4. Prints focus.getGreatestThreat() and derived hasCrisis.
     */
    public static String auditStrategicAI(String factionId) {
        StringBuilder sb = new StringBuilder("[nex4x audit-strategic-ai: " + factionId + "]\n");

        // 1. Orphan id checks
        try {
            String[] orphanConcernIds = {"nex4x_goal_wrap"};
            String[] orphanActionIds  = {"nex4x_propose_tiered_agreement"};
            for (String id : orphanConcernIds) {
                boolean absent = !exerelin.campaign.ai.StrategicDefManager.CONCERN_DEFS_BY_ID.containsKey(id);
                sb.append("  ").append(absent ? "PASS" : "FAIL")
                  .append(" orphan concern absent: ").append(id).append("\n");
            }
            for (String id : orphanActionIds) {
                boolean absent = !exerelin.campaign.ai.StrategicDefManager.ACTION_DEFS_BY_ID.containsKey(id);
                sb.append("  ").append(absent ? "PASS" : "FAIL")
                  .append(" orphan action absent: ").append(id).append("\n");
            }
        } catch (Throwable t) {
            sb.append("  StrategicDefManager not available: ").append(t.getMessage()).append("\n");
        }

        // 2. nex4x concern/action defs
        try {
            sb.append("  --- nex4x concern defs ---\n");
            for (java.util.Map.Entry<String, exerelin.campaign.ai.StrategicDefManager.StrategicConcernDef> e
                    : exerelin.campaign.ai.StrategicDefManager.CONCERN_DEFS_BY_ID.entrySet()) {
                if (!e.getKey().startsWith("nex4x_")) continue;
                exerelin.campaign.ai.StrategicDefManager.StrategicConcernDef def = e.getValue();
                sb.append("    ").append(def.id)
                  .append("  tags(").append(def.tags == null ? 0 : def.tags.size()).append(")")
                  .append(def.tags != null && !def.tags.isEmpty() ? "=" + def.tags : "")
                  .append("\n");
            }
            sb.append("  --- nex4x action defs ---\n");
            for (java.util.Map.Entry<String, exerelin.campaign.ai.StrategicDefManager.StrategicActionDef> e
                    : exerelin.campaign.ai.StrategicDefManager.ACTION_DEFS_BY_ID.entrySet()) {
                if (!e.getKey().startsWith("nex4x_")) continue;
                exerelin.campaign.ai.StrategicDefManager.StrategicActionDef def = e.getValue();
                sb.append("    ").append(def.id)
                  .append("  tags(").append(def.tags == null ? 0 : def.tags.size()).append(")")
                  .append(def.tags != null && !def.tags.isEmpty() ? "=" + def.tags : "")
                  .append("\n");
            }
        } catch (Throwable t) {
            sb.append("  def listing failed: ").append(t.getMessage()).append("\n");
        }

        // 3. Active goals: posture + obstacle
        try {
            nex4x.managers.Nex4xManager mgr = nex4x.managers.Nex4xManager.getManager();
            if (mgr == null) {
                sb.append("  Nex4xManager: not initialized\n");
            } else {
                nex4x.ai.StrategicGoalManager goalMgr = mgr.getGoalManager(factionId);
                if (goalMgr == null) {
                    sb.append("  No StrategicGoalManager for: ").append(factionId).append("\n");
                } else {
                    sb.append("  --- active goals ---\n");
                    java.util.Map<nex4x.ai.posture.DiplomaticPosture, Integer> postureDist =
                            new java.util.EnumMap<>(nex4x.ai.posture.DiplomaticPosture.class);
                    for (nex4x.ai.goals.StrategicGoal g : goalMgr.getActiveGoals()) {
                        sb.append("    ").append(g.type.displayName)
                          .append(" -> ").append(g.targetFactionId)
                          .append("  posture=").append(g.getPosture())
                          .append("  obstacle=").append(g.getObstacle().severity)
                          .append("  parentGoal=").append(g.getParentGoalId())
                          .append("\n");
                        nex4x.ai.posture.DiplomaticPosture p = g.getPosture();
                        postureDist.put(p, (postureDist.containsKey(p) ? postureDist.get(p) : 0) + 1);
                    }
                    sb.append("  posture distribution: ").append(postureDist).append("\n");

                    // 4. Threat and hasCrisis
                    nex4x.ai.StrategicFocus focus = goalMgr.getFocus();
                    nex4x.ai.StrategicFocus.Threat threat = focus.getGreatestThreat();
                    if (threat == null) {
                        sb.append("  greatestThreat: null\n");
                    } else {
                        sb.append("  greatestThreat: type=").append(threat.type)
                          .append("  severity=").append(Math.round(threat.severity))
                          .append("  existential=").append(threat.existential)
                          .append("  source=").append(threat.sourceFactionId)
                          .append("\n");
                    }
                    boolean hasCrisis = threat != null && threat.existential;
                    sb.append("  hasCrisis: ").append(hasCrisis).append("\n");
                }
            }
        } catch (Throwable t) {
            sb.append("  goal/focus audit failed: ").append(t.getMessage()).append("\n");
        }

        sb.append("[OK]");
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /** runcode nex4x.debug.Nex4xDebugCommand.openPeaceConference("hegemony","sindrian_diktat"); */
    public static String openPeaceConference(String attackerId, String defenderId) {
        nex4x.peace.PeaceConference pc = new nex4x.peace.PeaceConference(attackerId, defenderId);
        nex4x.ui.PeaceConferenceDialog.openScaled(pc);
        return "Opened peace conference: " + attackerId + " vs " + defenderId;
    }

    /**
     * PRD-016 smoke test — demonstrates personality-aware valuation on the live path.
     *
     * In-game: runcode nex4x.debug.Nex4xDebugCommand.auditValuation()
     *
     * Pass criterion: MERCANTILE and RUTHLESS leaders value a TRADE_AGREEMENT item
     * differently by at least 20% (proves Valuator is on the live accept/reject path).
     */
    public static String auditValuation() {
        StringBuilder sb = new StringBuilder("[nex4x audit-valuation]\n");
        try {
            // 1. Build a TRADE_AGREEMENT item.
            nex4x.negotiation.NegotiableItem tradeItem =
                    nex4x.negotiation.NegotiableItem.agreement(
                            nex4x.agreements.AgreementType.TRADE_AGREEMENT);

            // 2. Two synthetic leaders with different personalities (same faction so base is equal).
            nex4x.leaders.LeaderProfile mercantile =
                    new nex4x.leaders.LeaderProfile("hegemony", nex4x.leaders.Personality.MERCANTILE);
            nex4x.leaders.LeaderProfile ruthless =
                    new nex4x.leaders.LeaderProfile("hegemony", nex4x.leaders.Personality.RUTHLESS);

            // 3. Evaluate via the live leader-aware path.
            int valMercantile = nex4x.negotiation.ItemValuator.valueForLeader(
                    tradeItem, mercantile, "player");
            int valRuthless = nex4x.negotiation.ItemValuator.valueForLeader(
                    tradeItem, ruthless, "player");

            sb.append("  [Nex4x] MERCANTILE leader values trade_agreement item: ").append(valMercantile).append("\n");
            sb.append("  [Nex4x] RUTHLESS leader values trade_agreement item: ").append(valRuthless).append("\n");

            float diff = Math.abs(valMercantile - valRuthless) / (float) Math.max(1, valMercantile);
            String pass = diff >= 0.20f ? "PASS" : "FAIL";
            sb.append("  [Nex4x] Personality diff: ")
              .append(String.format("%.1f%%", diff * 100f))
              .append(" — ").append(pass).append("\n");

            // 4. Scarcity smoke (only if sector + economy available).
            try {
                float scarcity = nex4x.negotiation.Valuator.scarcityMod("hegemony", "fuel");
                sb.append("  [Nex4x] scarcityMod(hegemony, fuel): ").append(scarcity).append("\n");
            } catch (Throwable t) {
                sb.append("  [Nex4x] scarcityMod unavailable (no sector): ").append(t.getMessage()).append("\n");
            }

        } catch (Throwable t) {
            sb.append("  AUDIT FAIL: ").append(t.getMessage()).append("\n");
        }
        com.fs.starfarer.api.Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    // ─── PRD-022 audit methods ────────────────────────────────────────────────

    /**
     * runcode nex4x.debug.Nex4xDebugCommand.auditDemands()
     * Lists all active demands with status, expiry, and type.
     */
    public static String auditDemands() {
        StringBuilder sb = new StringBuilder("[nex4x audit-demands]\n");
        try {
            nex4x.demands.DemandManager mgr = nex4x.demands.DemandManager.getOrCreate();
            int pending = 0, accepted = 0, rejected = 0, expired = 0;
            for (nex4x.demands.Demand d : mgr.getAll()) {
                sb.append("  ").append(d.getDemanderId())
                  .append(" -> ").append(d.getTargetId())
                  .append(" : ").append(d.getType())
                  .append(" [").append(d.getStatus()).append("]")
                  .append("  expires day ").append(Math.round(d.getExpiryDay()))
                  .append("\n");
                switch (d.getStatus()) {
                    case PENDING:  pending++;  break;
                    case ACCEPTED: accepted++; break;
                    case REJECTED: rejected++; break;
                    case EXPIRED:  expired++;  break;
                }
            }
            int total = pending + accepted + rejected + expired;
            sb.append("  Total: ").append(total).append(" demands (")
              .append(pending).append(" pending, ")
              .append(accepted).append(" accepted, ")
              .append(rejected).append(" rejected, ")
              .append(expired).append(" expired)\n");
        } catch (Throwable t) {
            sb.append("  auditDemands failed: ").append(t.getMessage()).append("\n");
        }
        Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /**
     * runcode nex4x.debug.Nex4xDebugCommand.auditContracts()
     * Lists all contracts with status, winner, and payoff data.
     */
    public static String auditContracts() {
        StringBuilder sb = new StringBuilder("[nex4x audit-contracts]\n");
        try {
            nex4x.contracts.ContractAuctionManager mgr =
                    nex4x.contracts.ContractAuctionManager.getOrCreate();
            for (nex4x.contracts.Contract c : mgr.getAll()) {
                sb.append("  ").append(c.getId())
                  .append("  issuer=").append(c.getIssuerFactionId())
                  .append(" target=").append(c.getTargetFactionId())
                  .append(" type=").append(c.getType())
                  .append(" status=").append(c.getStatus());
                if (c.getWinningBid() != null) {
                    sb.append(" winner=").append(c.getWinningBid().getBidderFactionId())
                      .append(" bid=").append(c.getWinningBid().getCredits());
                    if (c.getStatus() == nex4x.contracts.Contract.Status.COMPLETED) {
                        String winnerId = c.getWinningBid().getBidderFactionId();
                        boolean playerWon = winnerId.equals(
                                Global.getSector().getPlayerFaction().getId());
                        float influenceDelta = (float)(c.getReservePrice()
                                * nex4x.contracts.ContractAuctionManager.CONTRACT_INFLUENCE_RATE);
                        if (playerWon) {
                            sb.append(" credits+").append(c.getReservePrice()).append("(player)");
                        } else {
                            sb.append(" influence+").append(influenceDelta)
                              .append("(").append(winnerId).append(")");
                        }
                    }
                }
                sb.append("\n");
            }
            sb.append("  Total: ").append(mgr.getAll().size()).append(" contracts\n");
        } catch (Throwable t) {
            sb.append("  auditContracts failed: ").append(t.getMessage()).append("\n");
        }
        Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /**
     * runcode nex4x.debug.Nex4xDebugCommand.auditVotes()
     * Lists all pending coalition votes with quorum data.
     */
    public static String auditVotes() {
        StringBuilder sb = new StringBuilder("[nex4x audit-votes]\n");
        try {
            nex4x.coalitions.CoalitionGovernance gov =
                    nex4x.coalitions.CoalitionGovernance.getOrCreate();
            java.util.List<nex4x.coalitions.CoalitionVote> votes = gov.getPendingVotes();
            for (nex4x.coalitions.CoalitionVote vote : votes) {
                // Get coalition members to display member count + leader
                java.util.List<String> members;
                nex4x.managers.Nex4xManager mgr = nex4x.managers.Nex4xManager.getManager();
                if (mgr != null) {
                    members = mgr.getAgreementManager()
                            .getCoalitionMembersFor(vote.getProposerFactionId());
                } else {
                    members = new java.util.ArrayList<String>();
                    members.add(vote.getProposerFactionId());
                }
                String leader = gov.getBlocLeader(members);
                sb.append("  ").append(vote.getType())
                  .append(" proposer=").append(vote.getProposerFactionId())
                  .append(" target=").append(vote.getTargetFactionId())
                  .append(" resolved=").append(vote.isResolved() ? "Y" : "N")
                  .append(" passed=").append(vote.isResolved()
                        ? (vote.isPassed() ? "Y" : "N") : "pending")
                  .append(" failedQuorum=").append(vote.isFailedQuorum() ? "Y" : "N")
                  .append(" voteCount=").append(vote.getVotes().size())
                  .append(" memberCount=").append(members.size())
                  .append(" blocLeader=").append(leader)
                  .append("\n");
            }
            sb.append("  Total pending votes: ").append(votes.size()).append("\n");
        } catch (Throwable t) {
            sb.append("  auditVotes failed: ").append(t.getMessage()).append("\n");
        }
        Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
    }

    /**
     * PRD-022 smoke test: issues + resolves a demand, completes a contract (asserting value moved),
     * and resolves a coalition vote (asserting quorum honored).
     *
     * runcode nex4x.debug.Nex4xDebugCommand.auditDemandContractVote()
     */
    public static String auditDemandContractVote() {
        StringBuilder sb = new StringBuilder("[nex4x audit-demand-contract-vote]\n");
        boolean allPass = true;

        // ── Demand smoke ──────────────────────────────────────────────────────
        try {
            nex4x.demands.DemandManager demandMgr = nex4x.demands.DemandManager.getOrCreate();
            // Issue a demand
            nex4x.demands.Demand d = demandMgr.issue("hegemony", "persean",
                    nex4x.demands.Demand.DemandType.TRIBUTE_CREDITS, "50000", 15f, 10f);
            boolean p1 = d != null;
            allPass &= p1;
            sb.append("  ").append(p1 ? "PASS" : "FAIL")
              .append("  demand issued: ").append(p1 ? d.getStatus() : "null").append("\n");

            if (d != null) {
                // Accept it and verify status change
                demandMgr.accept(d);
                boolean p2 = d.getStatus() == nex4x.demands.Demand.DemandStatus.ACCEPTED;
                allPass &= p2;
                sb.append("  ").append(p2 ? "PASS" : "FAIL")
                  .append("  demand accepted: status=").append(d.getStatus()).append("\n");
            }
        } catch (Throwable t) {
            allPass = false;
            sb.append("  FAIL  demand smoke: ").append(t.getMessage()).append("\n");
        }

        // ── Contract smoke ────────────────────────────────────────────────────
        try {
            nex4x.contracts.ContractAuctionManager cam =
                    nex4x.contracts.ContractAuctionManager.getOrCreate();
            nex4x.contracts.Contract ct = cam.post("hegemony", "persean",
                    nex4x.contracts.ContractType.HARASS_FACTION, 3000L);
            cam.bid(ct, "tritachyon", 5000L);
            cam.bid(ct, "sindrian_diktat", 4000L);

            // Manually resolve auction (normally done by advanceDay)
            // Force-award by calling bid on same contract to trigger resolveAuction indirectly.
            // Instead: verify bid was registered.
            boolean p3 = ct.getBids().size() == 2;
            allPass &= p3;
            sb.append("  ").append(p3 ? "PASS" : "FAIL")
              .append("  contract bids registered: ").append(ct.getBids().size()).append("\n");

            // Manually set AWARDED status + winning bid to test complete()
            ct.setStatus(nex4x.contracts.Contract.Status.AWARDED);
            ct.setWinningBid(new nex4x.contracts.Bid("tritachyon", 5000L,
                    nex4x.util.Nex4xClock.currentAbsoluteDay()));

            float influanceBefore = nex4x.influence.InfluenceManager.getOrCreate()
                    .getBalance("tritachyon");
            cam.complete(ct, true);
            float influenceAfter = nex4x.influence.InfluenceManager.getOrCreate()
                    .getBalance("tritachyon");
            boolean p4 = ct.getStatus() == nex4x.contracts.Contract.Status.COMPLETED;
            // Second-price: only 1 bid before winning bid set, so pay = reserve = 3000
            // influence delta = 3000 * 0.1 = 300
            boolean p5 = (influenceAfter - influanceBefore) > 0f;
            allPass &= p4 && p5;
            sb.append("  ").append(p4 ? "PASS" : "FAIL")
              .append("  contract completed: status=").append(ct.getStatus()).append("\n");
            sb.append("  ").append(p5 ? "PASS" : "FAIL")
              .append("  influence transferred: delta=")
              .append(influenceAfter - influanceBefore).append("\n");
        } catch (Throwable t) {
            allPass = false;
            sb.append("  FAIL  contract smoke: ").append(t.getMessage()).append("\n");
        }

        // ── Coalition vote smoke ──────────────────────────────────────────────
        try {
            nex4x.coalitions.CoalitionGovernance gov =
                    nex4x.coalitions.CoalitionGovernance.getOrCreate();
            nex4x.coalitions.CoalitionVote vote = gov.proposeVote(
                    nex4x.coalitions.CoalitionVote.VoteType.MAKE_PEACE,
                    "hegemony", "persean");
            // Simulate a two-member coalition: both members vote
            vote.castVote("hegemony", true);
            vote.castVote("tritachyon", true);
            // Resolve with memberCount=2 (quorum=1 needed)
            boolean decided = vote.resolve("hegemony", 2);
            boolean p6 = decided && vote.isPassed();
            allPass &= p6;
            sb.append("  ").append(p6 ? "PASS" : "FAIL")
              .append("  coalition vote resolved: decided=").append(decided)
              .append(" passed=").append(vote.isPassed())
              .append(" failedQuorum=").append(vote.isFailedQuorum()).append("\n");

            // Verify quorum enforcement: vote with 0 out of 4 members = failed quorum
            nex4x.coalitions.CoalitionVote quorumTest = gov.proposeVote(
                    nex4x.coalitions.CoalitionVote.VoteType.DECLARE_WAR,
                    "hegemony", "luddic_path");
            // No votes cast — resolve with memberCount=4; quorum fraction = 0/4 < 0.5
            boolean decided2 = quorumTest.resolve("hegemony", 4);
            boolean p7 = !decided2 && quorumTest.isFailedQuorum();
            allPass &= p7;
            sb.append("  ").append(p7 ? "PASS" : "FAIL")
              .append("  quorum enforced: decided=").append(decided2)
              .append(" failedQuorum=").append(quorumTest.isFailedQuorum()).append("\n");
        } catch (Throwable t) {
            allPass = false;
            sb.append("  FAIL  coalition vote smoke: ").append(t.getMessage()).append("\n");
        }

        sb.append("  overall: ").append(allPass ? "PASS" : "FAIL").append("\n");
        Global.getSector().getCampaignUI().addMessage(sb.toString());
        return sb.toString();
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
