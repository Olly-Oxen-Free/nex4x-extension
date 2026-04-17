package nex4x.declarations;

import com.fs.starfarer.api.Global;
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

    /** Advance: prune expired/withdrawn declarations. */
    public void advanceDay() {
        Iterator<Declaration> it = declarations.iterator();
        while (it.hasNext()) {
            Declaration d = it.next();
            if (!d.isActive()) {
                it.remove();
            }
        }
    }
}
