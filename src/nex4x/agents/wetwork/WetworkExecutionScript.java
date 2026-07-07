package nex4x.agents.wetwork;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agents.security.DetectionEngine;
import nex4x.ui.viceroy.WetworkHandler;
import org.apache.log4j.Logger;

import java.io.Serializable;

/**
 * Resolves a purchased wetwork contract some days after it is signed.
 *
 * Lifecycle (self-terminating {@link #isDone()}):
 *   1. Waits until {@code triggerDay} (7-14 days after signing, randomised at construction).
 *   2. Picks the target faction's most valuable market and rolls success vs. discovery.
 *      Success chance = 0.70 minus a penalty scaled by that market's detection strength
 *      ({@link DetectionEngine#getMarketDetection}, which already folds in cyber-security,
 *      intelligence-bureau and embassy presence), clamped to [0.25, 0.90].
 *   3a. SUCCESS: applies a timed −2 stability modifier to the market and keeps the script alive
 *       until {@code cleanupDay} (+60 days), when the modifier is removed and the script ends.
 *   3b. FAILURE: the operative is caught — {@link WetworkHandler#onDiscovered} fires the rep hit
 *       and espionage memory, and the script ends immediately.
 *   In both cases a {@link WetworkResultIntel} is delivered to the player.
 *
 * Persisted via {@code Global.getSector().addScript}; Serializable so it survives saves.
 */
public class WetworkExecutionScript implements EveryFrameScript, Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(WetworkExecutionScript.class);

    private static final String STABILITY_PREFIX = "nex4x_wetwork_";
    private static final float  STABILITY_HIT     = -2f;
    private static final float  BASE_SUCCESS       = 0.70f;
    private static final float  MIN_SUCCESS        = 0.25f;
    private static final float  MAX_SUCCESS        = 0.90f;
    private static final float  CLEANUP_DAYS       = 60f;

    private final String contractId;
    private final String marketFactionId;   // viceroy faction that brokered the deal
    private final String targetFactionId;   // faction being hit
    private final float  triggerDay;        // absolute day the outcome resolves

    private boolean resolved;               // outcome rolled
    private boolean success;                // stability strike landed
    private String  hitMarketId;            // market carrying the stability modifier (success only)
    private float   cleanupDay;             // absolute day to lift the modifier (success only)
    private boolean done;

    public WetworkExecutionScript(String marketFactionId, String targetFactionId, String contractId) {
        this.marketFactionId = marketFactionId;
        this.targetFactionId = targetFactionId;
        this.contractId = contractId;
        float now = nex4x.util.Nex4xClock.currentAbsoluteDay();
        // 7-14 days out.
        this.triggerDay = now + 7f + (float) (Math.random() * 7f);
        this.resolved = false;
        this.done = false;
    }

    @Override
    public boolean isDone() { return done; }

    @Override
    public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        if (done) return;
        float today = nex4x.util.Nex4xClock.currentAbsoluteDay();

        if (!resolved) {
            if (today < triggerDay) return;
            resolve(today);
            return;
        }

        // Post-success cleanup phase: lift the timed stability modifier.
        if (success && today >= cleanupDay) {
            liftStabilityModifier();
            done = true;
        }
    }

    private void resolve(float today) {
        resolved = true;

        MarketAPI target = pickTargetMarket();
        if (target == null) {
            // No reachable market to strike — contract fizzles, player still gets word.
            log.info("[Nex4x] Wetwork " + contractId + ": no eligible target market for "
                    + targetFactionId + " — contract aborted");
            deliverIntel(null, false, false);
            done = true;
            return;
        }

        float detection = DetectionEngine.getMarketDetection(target);
        float successChance = BASE_SUCCESS - Math.min(0.45f, detection / 120f);
        if (successChance < MIN_SUCCESS) successChance = MIN_SUCCESS;
        if (successChance > MAX_SUCCESS) successChance = MAX_SUCCESS;

        success = Math.random() < successChance;
        log.info("[Nex4x] Wetwork " + contractId + " resolving: target=" + targetFactionId
                + " market=" + target.getId() + " detection=" + detection
                + " p(success)=" + successChance + " -> " + (success ? "SUCCESS" : "DISCOVERY"));

        if (success) {
            target.getStability().modifyFlat(STABILITY_PREFIX + contractId, STABILITY_HIT,
                    "Assassination aftermath");
            hitMarketId = target.getId();
            cleanupDay = today + CLEANUP_DAYS;
            deliverIntel(target.getName(), true, false);
            // Clean success: player is not implicated, so no faction memory is created.
        } else {
            // Caught: wire the (previously dangling) discovery consequences for real.
            try {
                WetworkHandler.onDiscovered(targetFactionId);
            } catch (Throwable t) {
                log.warn("[Nex4x] Wetwork " + contractId + " onDiscovered failed: " + t.getMessage(), t);
            }
            deliverIntel(target.getName(), false, true);
            done = true;
        }
    }

    private void liftStabilityModifier() {
        if (hitMarketId == null) return;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (hitMarketId.equals(m.getId())) {
                m.getStability().unmodifyFlat(STABILITY_PREFIX + contractId);
                log.info("[Nex4x] Wetwork " + contractId + ": stability modifier lifted from "
                        + hitMarketId);
                return;
            }
        }
    }

    /** Largest non-hidden market owned by the target faction, or null. */
    private MarketAPI pickTargetMarket() {
        MarketAPI best = null;
        int bestSize = -1;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m == null || m.isHidden()) continue;
            if (!targetFactionId.equals(m.getFactionId())) continue;
            if (m.getSize() > bestSize) {
                bestSize = m.getSize();
                best = m;
            }
        }
        return best;
    }

    private void deliverIntel(String marketName, boolean success, boolean discovered) {
        try {
            WetworkResultIntel intel = new WetworkResultIntel(
                    targetFactionId, marketFactionId, marketName, success, discovered);
            Global.getSector().getIntelManager().addIntel(intel, false);
        } catch (Throwable t) {
            log.warn("[Nex4x] Wetwork " + contractId + " intel delivery failed: " + t.getMessage(), t);
        }
    }
}
