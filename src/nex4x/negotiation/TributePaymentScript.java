package nex4x.negotiation;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.MutableValue;
import nex4x.influence.InfluenceManager;
import nex4x.influence.InfluenceSource;
import nex4x.managers.Nex4xManager;
import nex4x.pressure.PressureManager;
import nex4x.pressure.PressureSource;
import org.apache.log4j.Logger;

import java.io.Serializable;

/**
 * Recurring tribute payment. Added to the sector via {@code Global.getSector().addScript(...)}
 * at deal execution time and persisted in the save. Every {@link #CYCLE_DAYS} days it moves
 * {@code creditsPerMonth} from the payer to the receiver:
 *
 * <ul>
 *   <li>Player is receiver — credits are added to the player fleet cargo.</li>
 *   <li>Player is payer — credits are subtracted; if the player cannot pay, the tribute is
 *       cancelled and the receiver files a grievance (mirrors the demand-reject path).</li>
 *   <li>NPC ↔ NPC — a scaled {@link InfluenceManager} lump adjustment is applied (credit totals
 *       are far larger than the influence economy, so the transfer is scaled down).</li>
 * </ul>
 *
 * The script terminates ({@link #isDone()} == true) once the duration elapses or the payer
 * defaults, writing a completion / default memory via {@link nex4x.managers.MemoryManager}.
 */
public class TributePaymentScript implements EveryFrameScript, Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(TributePaymentScript.class);

    public static final float CYCLE_DAYS = 30f;
    /** Grievance pressure applied to a defaulting payer (mirrors DemandManager.REJECTION_GRIEVANCE). */
    private static final float DEFAULT_GRIEVANCE = 20f;
    /** Divisor mapping credits → influence for NPC↔NPC transfers (keeps lump within the influence economy). */
    private static final float CREDITS_TO_INFLUENCE = 1000f;

    private final String payerFactionId;
    private final String receiverFactionId;
    private final int creditsPerMonth;
    private float nextPaymentDay;
    private final float endDay;
    private boolean done;

    public TributePaymentScript(String payerFactionId, String receiverFactionId,
                                int creditsPerMonth, float durationDays) {
        this.payerFactionId = payerFactionId;
        this.receiverFactionId = receiverFactionId;
        this.creditsPerMonth = creditsPerMonth;
        float today = nex4x.util.Nex4xClock.currentAbsoluteDay();
        this.nextPaymentDay = today + CYCLE_DAYS;
        this.endDay = today + Math.max(CYCLE_DAYS, durationDays);
        this.done = false;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) return;
        if (Global.getSector() == null) return;
        float today = nex4x.util.Nex4xClock.currentAbsoluteDay();
        // Catch up any due payments (handles multi-day time skips robustly).
        while (!done && today >= nextPaymentDay) {
            boolean paid = processPayment();
            if (!paid) {
                cancelForNonPayment();
                markDone();
                return;
            }
            nextPaymentDay += CYCLE_DAYS;
            if (nextPaymentDay > endDay + 0.5f) {
                completeNormally();
                markDone();
                return;
            }
        }
    }

    private void markDone() {
        done = true;
    }

    /** @return false only if the player is the payer and cannot afford the payment. */
    private boolean processPayment() {
        if (creditsPerMonth <= 0) return true;
        String playerId = Global.getSector().getPlayerFaction().getId();

        if (playerId.equals(receiverFactionId)) {
            MutableValue creds = Global.getSector().getPlayerFleet().getCargo().getCredits();
            creds.add(creditsPerMonth);
            Global.getSector().getCampaignUI().addMessage(
                    "Tribute received from " + factionName(payerFactionId)
                            + ": " + Misc.getDGSCredits(creditsPerMonth),
                    Misc.getPositiveHighlightColor());
            return true;
        }

        if (playerId.equals(payerFactionId)) {
            MutableValue creds = Global.getSector().getPlayerFleet().getCargo().getCredits();
            if (creds.get() < creditsPerMonth) {
                return false; // cannot pay — caller cancels
            }
            creds.subtract(creditsPerMonth);
            Global.getSector().getCampaignUI().addMessage(
                    "Tribute paid to " + factionName(receiverFactionId)
                            + ": " + Misc.getDGSCredits(creditsPerMonth),
                    Misc.getNegativeHighlightColor());
            return true;
        }

        // NPC ↔ NPC: scaled influence lump adjustment (no credit economy for NPC fleets).
        try {
            InfluenceManager infl = InfluenceManager.getOrCreate();
            float lump = creditsPerMonth / CREDITS_TO_INFLUENCE;
            if (lump > 0f) {
                infl.addLump(receiverFactionId, lump, InfluenceSource.TREASURY_TRANSFER);
                if (infl.canAfford(payerFactionId, lump)) {
                    infl.spend(payerFactionId, lump, InfluenceSource.TREASURY_TRANSFER);
                }
            }
        } catch (Throwable t) {
            log.info("[Nex4x] Tribute NPC influence transfer skipped: " + t.getMessage());
        }
        return true;
    }

    private void cancelForNonPayment() {
        log.info("[Nex4x] Tribute defaulted: " + payerFactionId + " could not pay "
                + creditsPerMonth + " cr to " + receiverFactionId + " — cancelling.");
        Global.getSector().getCampaignUI().addMessage(
                "You could not meet your tribute to " + factionName(receiverFactionId)
                        + ". The agreement is void and they are aggrieved.",
                Misc.getNegativeHighlightColor());
        try {
            PressureManager.getOrCreate().applyEvent(
                    receiverFactionId, payerFactionId, PressureSource.GRIEVANCE, DEFAULT_GRIEVANCE);
        } catch (Throwable t) {
            log.warn("[Nex4x] Tribute default grievance failed: " + t.getMessage());
        }
        writeMemory("tribute_defaulted", "defaulted after start");
    }

    private void completeNormally() {
        log.info("[Nex4x] Tribute completed: " + payerFactionId + " -> " + receiverFactionId
                + " (" + creditsPerMonth + " cr/cycle)");
        writeMemory("tribute_completed", creditsPerMonth + " cr/cycle honored to term");
    }

    private void writeMemory(String typeId, String details) {
        try {
            Nex4xManager mgr = Nex4xManager.getManager();
            if (mgr != null && mgr.getMemoryManager() != null) {
                mgr.getMemoryManager().createMemory(typeId, payerFactionId, receiverFactionId, details);
            }
        } catch (Throwable t) {
            log.warn("[Nex4x] Tribute memory write failed (" + typeId + "): " + t.getMessage());
        }
    }

    private static String factionName(String factionId) {
        FactionAPI f = Global.getSector().getFaction(factionId);
        return f != null ? f.getDisplayName() : factionId;
    }
}
