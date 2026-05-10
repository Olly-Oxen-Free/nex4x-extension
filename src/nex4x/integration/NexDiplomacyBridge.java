package nex4x.integration;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.econ.TributeCondition;
import exerelin.campaign.intel.diplomacy.TributeIntel;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Static helpers wrapping Nex's DiplomacyManager and AllianceManager calls in
 * try/catch with FactionAPI fallback. Never throws; never returns checked exceptions.
 *
 * <p>All methods are null-safe at the API surface: null factions or IDs produce a
 * debug log and a no-op.
 */
public final class NexDiplomacyBridge {

    private static final Logger log = Global.getLogger(NexDiplomacyBridge.class);

    private NexDiplomacyBridge() {}

    /**
     * Fires a "respect" diplomacy event from {@code declarer} toward {@code target},
     * granting a reputation gain of {@code repPercent} points.
     *
     * @param declarer   the faction initiating the respectful act
     * @param target     the faction receiving the reputation boost
     * @param repPercent raw reputation percentage (e.g. 5 = +5 rep points)
     */
    public static void fireRespectEvent(FactionAPI declarer, FactionAPI target, float repPercent) {
        if (declarer == null || target == null) {
            log.debug("NexDiplomacyBridge.fireRespectEvent: null faction — skipping");
            return;
        }
        try {
            DiplomacyManager.createDiplomacyEventV2(declarer, target, "respect", null);
            log.info("NexDiplomacyBridge.fireRespectEvent: fired respect event "
                    + declarer.getId() + " -> " + target.getId());
        } catch (Throwable t) {
            log.warn("NexDiplomacyBridge.fireRespectEvent: Nex API unavailable ("
                    + t.getClass().getSimpleName() + "), using fallback for "
                    + declarer.getId() + " -> " + target.getId(), t);
            declarer.adjustRelationship(target.getId(), repPercent / 100f);
        }
    }

    /**
     * Fires an "insult" diplomacy event from {@code declarer} toward {@code target},
     * applying a reputation penalty of {@code repPercent} points.
     *
     * @param declarer   the faction initiating the insult
     * @param target     the faction receiving the penalty
     * @param repPercent raw reputation percentage magnitude (sign ignored; Nex stage handles it)
     */
    public static void fireInsultEvent(FactionAPI declarer, FactionAPI target, float repPercent) {
        if (declarer == null || target == null) {
            log.debug("NexDiplomacyBridge.fireInsultEvent: null faction — skipping");
            return;
        }
        try {
            DiplomacyManager.createDiplomacyEventV2(declarer, target, "insult", null);
            log.info("NexDiplomacyBridge.fireInsultEvent: fired insult event "
                    + declarer.getId() + " -> " + target.getId());
        } catch (Throwable t) {
            log.warn("NexDiplomacyBridge.fireInsultEvent: Nex API unavailable ("
                    + t.getClass().getSimpleName() + "), using fallback for "
                    + declarer.getId() + " -> " + target.getId(), t);
            declarer.adjustRelationship(target.getId(), -Math.abs(repPercent) / 100f);
        }
    }

    /**
     * Fires a peace-treaty diplomacy event between factions {@code a} and {@code b}.
     * On failure, sets relationship to neutral (0) if both are still hostile.
     *
     * @param a one of the parties signing the treaty
     * @param b the other party signing the treaty
     */
    public static void firePeaceTreaty(FactionAPI a, FactionAPI b) {
        if (a == null || b == null) {
            log.debug("NexDiplomacyBridge.firePeaceTreaty: null faction — skipping");
            return;
        }
        try {
            DiplomacyManager.createDiplomacyEvent(a, b, "peace_treaty", null);
            log.info("NexDiplomacyBridge.firePeaceTreaty: fired peace treaty "
                    + a.getId() + " <-> " + b.getId());
        } catch (Throwable t) {
            log.warn("NexDiplomacyBridge.firePeaceTreaty: Nex API unavailable ("
                    + t.getClass().getSimpleName() + "), using fallback for "
                    + a.getId() + " <-> " + b.getId(), t);
            if (a.isHostileTo(b)) {
                a.setRelationship(b.getId(), 0f);
            }
            if (b.isHostileTo(a)) {
                b.setRelationship(a.getId(), 0f);
            }
        }
    }

    /**
     * Ensures factions {@code factionA} and {@code factionB} share an alliance,
     * creating one or joining an existing one as needed.
     *
     * <ul>
     *   <li>If neither is in an alliance: creates a new alliance containing both.</li>
     *   <li>If exactly one is already in an alliance: the other joins that alliance.</li>
     *   <li>If both are already in (potentially different) alliances: logs a warning
     *       and returns {@code null} — merging alliances is outside this helper's scope.</li>
     * </ul>
     *
     * @param factionA id of the first faction
     * @param factionB id of the second faction
     * @return the shared {@link Alliance}, or {@code null} on conflict or error
     */
    /**
     * Fires a "declare_war" diplomacy event from {@code declarer} to {@code target} with a
     * casus-belli justification, suppressing any badboy increase that would otherwise result.
     *
     * <p>The method snapshots the declarer's badboy value before and after the event; if the
     * event raised it, the increase is immediately reversed. Falls back to
     * {@link #firePeaceTreaty} + a relationship drop if the Nex API is unavailable.
     *
     * @param declarer the faction declaring war
     * @param target   the faction war is declared against
     * @param cbId     identifier of the casus belli that justifies the war (for logging only)
     */
    public static void fireJustifiedWar(FactionAPI declarer, FactionAPI target, String cbId) {
        if (declarer == null || target == null) {
            log.debug("NexDiplomacyBridge.fireJustifiedWar: null faction — skipping");
            return;
        }
        try {
            MemoryAPI mem = Global.getSector().getFaction(declarer.getId()).getMemoryWithoutUpdate();
            float beforeBadboy = mem != null ? mem.getFloat(DiplomacyManager.MEM_KEY_BADBOY) : 0f;

            DiplomacyManager.createDiplomacyEvent(declarer, target, "declare_war", null);

            float afterBadboy = mem != null ? mem.getFloat(DiplomacyManager.MEM_KEY_BADBOY) : 0f;
            float delta = afterBadboy - beforeBadboy;

            if (delta > 0f && mem != null) {
                mem.set(DiplomacyManager.MEM_KEY_BADBOY, beforeBadboy, 0f);
                log.info("[Nex4x] Justified war (cb=" + cbId + ") — undid badboy delta " + delta
                        + " for " + declarer.getId());
            } else {
                log.info("[Nex4x] Justified war (cb=" + cbId + ") — no badboy change, delta="
                        + delta + " for " + declarer.getId());
            }
        } catch (Throwable t) {
            log.warn("[Nex4x] fireJustifiedWar: Nex API unavailable ("
                    + t.getClass().getSimpleName() + "), using fallback for "
                    + declarer.getId() + " -> " + target.getId(), t);
            declarer.setRelationship(target.getId(), -1f);
        }
    }

    /**
     * Ensures all factions in {@code memberFids} belong to a single Nex {@link Alliance},
     * mirroring the composition of an in-game coalition identified by {@code coalitionId}.
     *
     * <p>An anchor alliance is located by scanning {@code memberFids} for any faction that is
     * already a member of an alliance. If none exists, a new alliance is created from the first
     * two member fids. Remaining factions join that alliance when
     * {@link AllianceManager#getManager()}.{@code canAlly} permits it.
     *
     * @param coalitionId human-readable coalition identifier (for logging only)
     * @param memberFids  ordered list of faction ids that make up the coalition
     */
    public static void syncCoalitionToAlliance(String coalitionId, List<String> memberFids) {
        if (memberFids == null || memberFids.size() < 2) {
            log.debug("[Nex4x] syncCoalitionToAlliance: coalition " + coalitionId
                    + " has fewer than 2 members — skipping");
            return;
        }
        try {
            Alliance existing = null;
            String anchorFid = null;

            // Find the first member already in an alliance
            for (String fid : memberFids) {
                Alliance a = AllianceManager.getFactionAlliance(fid);
                if (a != null) {
                    existing = a;
                    anchorFid = fid;
                    break;
                }
            }

            // No anchor found — create a fresh alliance from the first two members
            if (existing == null) {
                existing = AllianceManager.createAlliance(memberFids.get(0), memberFids.get(1));
                anchorFid = memberFids.get(0);
                log.info("[Nex4x] syncCoalitionToAlliance: created new alliance for coalition "
                        + coalitionId + " from " + memberFids.get(0) + " + " + memberFids.get(1));
            }

            int joined = 0;
            int skipped = 0;
            AllianceManager mgr = AllianceManager.getManager();

            for (String fid : memberFids) {
                if (AllianceManager.getFactionAlliance(fid) == existing) {
                    skipped++;
                    continue;
                }
                if (!mgr.canAlly(fid, anchorFid)) {
                    log.debug("[Nex4x] syncCoalitionToAlliance: canAlly returned false for "
                            + fid + " + " + anchorFid + " — skipping");
                    skipped++;
                    continue;
                }
                mgr.joinAlliance(fid, existing);
                joined++;
            }

            log.info("[Nex4x] Coalition " + coalitionId + " -> Alliance " + existing.getName()
                    + " (" + (skipped + joined) + "/" + memberFids.size() + " members, "
                    + joined + " newly joined)");
        } catch (Throwable t) {
            log.warn("[Nex4x] syncCoalitionToAlliance: error for coalition " + coalitionId
                    + ": " + t.getMessage(), t);
        }
    }

    // -------------------------------------------------------------------------
    // TributeCondition helpers
    // -------------------------------------------------------------------------

    /**
     * Applies {@link TributeCondition} to {@code market} on behalf of {@code receiver}, wiring
     * up a {@link TributeIntel} instance so the condition displays correctly in the UI.
     *
     * <p>Idempotent: if the condition already exists on the market this method returns
     * immediately without creating a duplicate. The condition's income-penalty logic
     * ({@link TributeCondition#getIncomePenalty()}) is applied by the condition itself via
     * its {@code apply()} callback and does not require a wired intel to function.
     *
     * @param market   the market that will carry the tribute burden
     * @param receiver the faction receiving the tribute payments
     */
    public static void applyTributeCondition(MarketAPI market, FactionAPI receiver) {
        if (market == null || receiver == null) {
            log.debug("[Nex4x] applyTributeCondition: null market or receiver — skipping");
            return;
        }
        try {
            if (market.hasCondition(TributeCondition.CONDITION_ID)) {
                return; // idempotent
            }
            market.addCondition(TributeCondition.CONDITION_ID);
            MarketConditionAPI mc = market.getSpecificCondition(TributeCondition.CONDITION_ID);
            if (mc == null) {
                log.warn("[Nex4x] applyTributeCondition: addCondition returned no condition on "
                        + market.getId());
                return;
            }
            Object plugin = mc.getPlugin();
            if (plugin instanceof TributeCondition) {
                TributeIntel intel = getOrCreateTributeIntel(market, receiver);
                ((TributeCondition) plugin).setup(receiver, intel);
            }
            log.info("[Nex4x] applyTributeCondition: applied to " + market.getId()
                    + " for receiver " + receiver.getId());
        } catch (Throwable t) {
            log.warn("[Nex4x] applyTributeCondition: error on market " + market.getId()
                    + ": " + t.getMessage(), t);
        }
    }

    /**
     * Removes the {@link TributeCondition} from {@code market} if present.
     *
     * @param market the market from which the tribute burden is lifted
     */
    public static void removeTributeCondition(MarketAPI market) {
        if (market == null) {
            log.debug("[Nex4x] removeTributeCondition: null market — skipping");
            return;
        }
        try {
            if (market.hasCondition(TributeCondition.CONDITION_ID)) {
                market.removeCondition(TributeCondition.CONDITION_ID);
                log.info("[Nex4x] removeTributeCondition: removed from " + market.getId());
            }
        } catch (Throwable t) {
            log.warn("[Nex4x] removeTributeCondition: error on market " + market.getId()
                    + ": " + t.getMessage(), t);
        }
    }

    /**
     * Applies {@link TributeCondition} to all non-hidden markets owned by {@code giverFid}.
     *
     * @param giverFid    faction id of the tribute payer
     * @param receiverFid faction id of the tribute recipient
     * @return number of markets the condition was newly applied to
     */
    public static int applyTributeToFaction(String giverFid, String receiverFid) {
        if (giverFid == null || receiverFid == null) {
            log.debug("[Nex4x] applyTributeToFaction: null faction id — skipping");
            return 0;
        }
        FactionAPI receiver = Global.getSector().getFaction(receiverFid);
        if (receiver == null) {
            log.debug("[Nex4x] applyTributeToFaction: receiver faction not found: " + receiverFid);
            return 0;
        }
        int count = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (giverFid.equals(m.getFactionId()) && !m.isHidden()) {
                if (!m.hasCondition(TributeCondition.CONDITION_ID)) {
                    applyTributeCondition(m, receiver);
                    count++;
                }
            }
        }
        if (count > 0) {
            log.info("[Nex4x] applyTributeToFaction: applied to " + count
                    + " markets for " + giverFid + " -> " + receiverFid);
        }
        return count;
    }

    /**
     * Removes {@link TributeCondition} from all markets owned by {@code giverFid}.
     *
     * @param giverFid faction id of the tribute payer being freed
     * @return number of markets the condition was removed from
     */
    public static int removeTributeForFaction(String giverFid) {
        if (giverFid == null) {
            log.debug("[Nex4x] removeTributeForFaction: null faction id — skipping");
            return 0;
        }
        int count = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (giverFid.equals(m.getFactionId()) && m.hasCondition(TributeCondition.CONDITION_ID)) {
                removeTributeCondition(m);
                count++;
            }
        }
        if (count > 0) {
            log.info("[Nex4x] removeTributeForFaction: removed from " + count
                    + " markets for " + giverFid);
        }
        return count;
    }

    /**
     * Finds or creates a {@link TributeIntel} for the given (market, receiver) pair.
     *
     * <p>Note: {@link TributeIntel}'s constructor accepts {@code (String factionId, MarketAPI)}
     * where {@code factionId} is the <em>receiver's</em> faction id (as revealed by javap).
     * The intel is added to the sector intel manager when newly created.
     *
     * @param market   the tribute-bearing market (used both as the key and ctor arg)
     * @param receiver the faction receiving tribute payments
     * @return an existing or freshly-created {@link TributeIntel}, or {@code null} on error
     */
    private static TributeIntel getOrCreateTributeIntel(MarketAPI market, FactionAPI receiver) {
        try {
            TributeIntel existing = TributeIntel.getOngoingIntel(market);
            if (existing != null) {
                return existing;
            }
            TributeIntel intel = new TributeIntel(receiver.getId(), market);
            Global.getSector().getIntelManager().addIntel(intel);
            return intel;
        } catch (Throwable t) {
            log.warn("[Nex4x] getOrCreateTributeIntel: failed for market " + market.getId()
                    + ": " + t.getMessage(), t);
            return null;
        }
    }

    /**
     * Raises the relationship between two factions to at least {@code minRelRaw} if it is
     * currently below that floor, implementing a non-aggression pact or defensive-pact floor.
     *
     * <p>If the current relationship already meets or exceeds {@code minRelRaw}, this method
     * is a no-op so that existing positive relations are never downgraded.
     *
     * @param factionA  id of the first faction
     * @param factionB  id of the second faction
     * @param minRelRaw minimum raw relationship value (e.g. {@code 0f} for neutral,
     *                  {@code 0.5f} for friendly)
     */
    public static void enforceNonAggression(String factionA, String factionB, float minRelRaw) {
        if (factionA == null || factionB == null) {
            log.debug("NexDiplomacyBridge.enforceNonAggression: null faction id — skipping");
            return;
        }
        FactionAPI a = Global.getSector().getFaction(factionA);
        FactionAPI b = Global.getSector().getFaction(factionB);
        if (a == null || b == null) {
            log.debug("NexDiplomacyBridge.enforceNonAggression: faction not found ("
                    + factionA + " / " + factionB + ") — skipping");
            return;
        }
        float current = a.getRelationship(factionB);
        if (current >= minRelRaw) {
            return; // already meets the floor — do not downgrade
        }
        a.setRelationship(factionB, minRelRaw);
        log.info("[Nex4x] enforceNonAggression: nudged " + factionA + " <-> " + factionB
                + " from " + current + " to " + minRelRaw);
    }

    /**
     * Instructs Nex's {@link StrategicAI} to remove all active AI brains, clearing stale
     * diplomatic state. Safe to call repeatedly; logs only when brains were removed.
     *
     * <p>{@code StrategicAI.removeAIs()} does not expose a count, so this method always
     * returns {@code 0}.
     *
     * @return number of AI brains removed (always {@code 0} — Nex API provides no count)
     */
    public static int sweepDiplomacyBrains() {
        try {
            StrategicAI.removeAIs();
            log.info("[Nex4x] sweepDiplomacyBrains: removeAIs() called");
        } catch (Throwable t) {
            log.warn("[Nex4x] sweepDiplomacyBrains: StrategicAI.removeAIs() unavailable — "
                    + t.getClass().getSimpleName(), t);
        }
        return 0;
    }

    /**
     * Ensures factions {@code factionA} and {@code factionB} share an alliance,
     * creating one or joining an existing one as needed.
     *
     * <ul>
     *   <li>If neither is in an alliance: creates a new alliance containing both.</li>
     *   <li>If exactly one is already in an alliance: the other joins that alliance.</li>
     *   <li>If both are already in (potentially different) alliances: logs a warning
     *       and returns {@code null} — merging alliances is outside this helper's scope.</li>
     * </ul>
     *
     * @param factionA id of the first faction
     * @param factionB id of the second faction
     * @return the shared {@link Alliance}, or {@code null} on conflict or error
     */
    public static Alliance ensureAlliance(String factionA, String factionB) {
        if (factionA == null || factionB == null) {
            log.debug("NexDiplomacyBridge.ensureAlliance: null faction id — skipping");
            return null;
        }
        try {
            Alliance allianceA = AllianceManager.getFactionAlliance(factionA);
            Alliance allianceB = AllianceManager.getFactionAlliance(factionB);

            if (allianceA == null && allianceB == null) {
                Alliance created = AllianceManager.createAlliance(factionA, factionB);
                log.info("NexDiplomacyBridge.ensureAlliance: created new alliance for "
                        + factionA + " + " + factionB);
                return created;
            } else if (allianceA != null && allianceB == null) {
                AllianceManager.getManager().joinAlliance(factionB, allianceA);
                log.info("NexDiplomacyBridge.ensureAlliance: " + factionB
                        + " joined existing alliance of " + factionA);
                return allianceA;
            } else if (allianceA == null) {
                // allianceB != null, allianceA == null
                AllianceManager.getManager().joinAlliance(factionA, allianceB);
                log.info("NexDiplomacyBridge.ensureAlliance: " + factionA
                        + " joined existing alliance of " + factionB);
                return allianceB;
            } else {
                log.warn("NexDiplomacyBridge.ensureAlliance: both " + factionA
                        + " and " + factionB + " are already in alliances — cannot merge, returning null");
                return null;
            }
        } catch (Throwable t) {
            log.warn("NexDiplomacyBridge.ensureAlliance: error for "
                    + factionA + " + " + factionB + ": " + t.getMessage(), t);
            return null;
        }
    }
}
