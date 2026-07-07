package nex4x.declarations;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages all active public declarations. Persisted in save via Nex4xManager.
 *
 * Key behaviors:
 * - Hostile declarations (Denounce, Rivalry) block negotiation between factions
 * - Withdrawing a positive declaration (Friendship, Guarantee) creates negative
 *   consequences (Broken Agreement memory, Oathbreaker badge risk)
 * - Only one declaration of each type can be active between a pair at a time
 */
public class DeclarationManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(DeclarationManager.class);
    private final List<Declaration> declarations = new ArrayList<Declaration>();

    /**
     * Issue a new declaration. Cancels any existing declaration of the same type
     * between the pair.
     */
    public Declaration declare(String declarerFactionId, String targetFactionId,
                                DeclarationType type) {
        if (declarerFactionId == null || targetFactionId == null || type == null) {
            log.warn("[Nex4x] declare(): null arg rejected");
            return null;
        }
        if (declarerFactionId.equals(targetFactionId)) {
            log.warn("[Nex4x] declare(): self-target rejected for " + declarerFactionId);
            return null;
        }
        if (!type.unilateral) {
            log.warn("[Nex4x] declare(): " + type + " is not unilateral; route through negotiation flow instead");
            return null;
        }
        // Cancel existing of same type between this pair
        Declaration existing = getDeclaration(declarerFactionId, targetFactionId, type);
        if (existing != null) {
            existing.withdraw();
            log.info("[Nex4x] Withdrew existing " + type.displayName
                    + " between " + declarerFactionId + " and " + targetFactionId);
        }

        Declaration decl = new Declaration(declarerFactionId, targetFactionId, type);
        declarations.add(decl);

        log.info("[Nex4x] Declaration: " + declarerFactionId + " declared "
                + type.displayName + " on " + targetFactionId);
        return decl;
    }

    /**
     * Withdraw a specific declaration type between two factions.
     * @return true if a declaration was found and withdrawn
     */
    public boolean withdraw(String factionA, String factionB, DeclarationType type) {
        Declaration decl = getDeclaration(factionA, factionB, type);
        if (decl != null && decl.isActive()) {
            decl.withdraw();
            log.info("[Nex4x] Withdrew " + type.displayName
                    + " between " + factionA + " and " + factionB);
            return true;
        }
        return false;
    }

    /**
     * Get active declaration of a specific type between two factions (either direction).
     */
    public Declaration getDeclaration(String factionA, String factionB,
                                       DeclarationType type) {
        for (Declaration d : declarations) {
            if (d.isActive() && d.getType() == type && d.isBetween(factionA, factionB)) {
                return d;
            }
        }
        return null;
    }

    /** Get all active declarations involving a faction. */
    public List<Declaration> getDeclarationsFor(String factionId) {
        List<Declaration> result = new ArrayList<Declaration>();
        for (Declaration d : declarations) {
            if (d.isActive() && d.involves(factionId)) {
                result.add(d);
            }
        }
        return result;
    }

    /** Get all active declarations between two specific factions. */
    public List<Declaration> getDeclarationsBetween(String factionA, String factionB) {
        List<Declaration> result = new ArrayList<Declaration>();
        for (Declaration d : declarations) {
            if (d.isActive() && d.isBetween(factionA, factionB)) {
                result.add(d);
            }
        }
        return result;
    }

    /**
     * Check if negotiation is blocked between two factions.
     * Returns true if any hostile declaration (Denounce or Rivalry) is active.
     */
    public boolean isNegotiationBlocked(String factionA, String factionB) {
        for (Declaration d : declarations) {
            if (d.isActive() && d.isBetween(factionA, factionB)
                    && d.getType().blocksNegotiation) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the blocking declaration between two factions, if any.
     * Returns the first hostile declaration found, or null.
     */
    public Declaration getBlockingDeclaration(String factionA, String factionB) {
        for (Declaration d : declarations) {
            if (d.isActive() && d.isBetween(factionA, factionB)
                    && d.getType().blocksNegotiation) {
                return d;
            }
        }
        return null;
    }

    /**
     * Get the total disposition modifier from all active declarations between two factions.
     */
    public float getDispositionModifier(String factionA, String factionB) {
        float total = 0;
        for (Declaration d : declarations) {
            if (d.isActive() && d.isBetween(factionA, factionB)) {
                total += d.getType().dispositionModifier;
            }
        }
        return total;
    }

    /** Check if a specific declaration type is active between two factions. */
    public boolean hasDeclaration(String factionA, String factionB, DeclarationType type) {
        return getDeclaration(factionA, factionB, type) != null;
    }

    /** Friendship declaration with optional receiver counter-demand already resolved. */
    public Declaration declareFriendship(String declarer, String target) {
        // Withdraw any prior friendship between this pair to prevent stacking.
        Declaration prior = getDeclaration(declarer, target, DeclarationType.FRIENDSHIP);
        if (prior != null) prior.setActive(false);

        DeclarationConfig cfg = DeclarationConfig.get(DeclarationType.FRIENDSHIP);
        Declaration d = new Declaration(declarer, target, DeclarationType.FRIENDSHIP);
        d.setExpiryDay(d.getCreationDay() + cfg.durationDays);
        d.setFlatRepApplied(cfg.flatRepBonus);
        declarations.add(d);

        FactionAPI dfa = Global.getSector().getFaction(declarer);
        FactionAPI tfa = Global.getSector().getFaction(target);
        nex4x.integration.NexDiplomacyBridge.fireRespectEvent(dfa, tfa, cfg.flatRepBonus);
        log.info("[Nex4x] Friendship declared: " + declarer + " → " + target);
        return d;
    }

    /** Denouncement — unilateral, immediate application. */
    public Declaration declareDenouncement(String declarer, String target) {
        Declaration prior = getDeclaration(declarer, target, DeclarationType.DENOUNCE);
        if (prior != null) prior.setActive(false);

        DeclarationConfig cfg = DeclarationConfig.get(DeclarationType.DENOUNCE);
        Declaration d = new Declaration(declarer, target, DeclarationType.DENOUNCE);
        d.setExpiryDay(d.getCreationDay() + cfg.durationDays);
        d.setFlatRepApplied(-cfg.flatRepPenalty);
        declarations.add(d);

        FactionAPI dfa = Global.getSector().getFaction(declarer);
        FactionAPI tfa = Global.getSector().getFaction(target);
        nex4x.integration.NexDiplomacyBridge.fireInsultEvent(dfa, tfa, cfg.flatRepPenalty);
        applyDenouncementRipple(declarer, target, cfg);
        log.info("[Nex4x] Denouncement declared: " + declarer + " → " + target);
        return d;
    }

    private void applyDenouncementRipple(String declarer, String target, DeclarationConfig cfg) {
        if (cfg.rippleRatio <= 0f) return;
        FactionAPI tf = Global.getSector().getFaction(target);
        if (tf == null) return;
        float rippleAmt = -cfg.flatRepPenalty * cfg.rippleRatio / 100f;
        FactionAPI df = Global.getSector().getFaction(declarer);
        for (FactionAPI other : Global.getSector().getAllFactions()) {
            if (other == tf) continue;
            if (other.getId().equals(declarer)) continue;
            if (other.isNeutralFaction() || other.isPlayerFaction()) continue;
            float rel = other.getRelationship(target);
            if (rel >= 0.50f) { // friends/allies of target
                nex4x.integration.NexDiplomacyBridge.fireInsultEvent(df, other, Math.abs(rippleAmt) * 100f);
            }
        }
    }

    /** Advance: daily rep gain, CB unlock, post-expiry decay, prune withdrawn/expired.
     *  @param elapsedDays days elapsed since last call (handles tick jitter and multi-day catch-up). */
    public void advanceDay(float elapsedDays) {
        if (elapsedDays <= 0f) elapsedDays = 1f;
        float now = nex4x.util.Nex4xClock.currentAbsoluteDay();
        Iterator<Declaration> it = declarations.iterator();
        while (it.hasNext()) {
            Declaration d = it.next();
            DeclarationConfig cfg = DeclarationConfig.get(d.getType());

            // Prune withdrawn declarations that haven't started expiry decay
            if (!d.isActive() && (d.getExpiryDay() < 0 || now < d.getExpiryDay())) {
                it.remove();
                continue;
            }

            boolean active = d.isActive() && (d.getExpiryDay() < 0 || now < d.getExpiryDay());

            if (active) {
                // Per-day drift uses adjustRelationship directly to avoid intel-log spam.
                // Friendship daily rep gain — adjustRelationship mirrors automatically.
                if (d.getType() == DeclarationType.FRIENDSHIP && cfg.dailyRepGain > 0f) {
                    FactionAPI df = Global.getSector().getFaction(d.getDeclarerFactionId());
                    if (df != null) {
                        df.adjustRelationship(d.getTargetFactionId(),
                                cfg.dailyRepGain * elapsedDays / 100f);
                    }
                }
                // Denouncement CB unlock at 3 active months
                if (d.getType() == DeclarationType.DENOUNCE && !d.isCbUnlocked()
                        && (now - d.getCreationDay()) >= cfg.cbUnlockDays) {
                    d.setCbUnlocked(true);
                    log.info("[Nex4x] Denouncement CB unlocked: " + d.getDeclarerFactionId()
                            + " vs " + d.getTargetFactionId());
                }
            } else if (d.getExpiryDay() >= 0 && now >= d.getExpiryDay()) {
                // Declaration has expired; linear decay over cfg.decayDaysAfterExpiry.
                if (cfg.decayDaysAfterExpiry > 0 && d.getFlatRepApplied() != 0f) {
                    float daysElapsed = now - d.getExpiryDay();
                    float decayStep = d.getFlatRepApplied() / (float) cfg.decayDaysAfterExpiry;
                    FactionAPI df = Global.getSector().getFaction(d.getDeclarerFactionId());
                    if (df != null) {
                        df.adjustRelationship(d.getTargetFactionId(),
                                -decayStep * elapsedDays / 100f);
                    }
                    if (daysElapsed >= cfg.decayDaysAfterExpiry) {
                        d.setActive(false);
                        it.remove();
                    }
                } else {
                    d.setActive(false);
                    it.remove();
                }
            }
        }
    }

    /** @deprecated callers should pass elapsed days; defaults to 1f for back-compat. */
    @Deprecated
    public void advanceDay() { advanceDay(1f); }
}
