package nex4x.managers;

import com.fs.starfarer.api.Global;
import nex4x.badges.BadgeType;
import nex4x.badges.FactionBadges;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class BadgeManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(BadgeManager.class);
    private final Map<String, FactionBadges> factionBadges = new HashMap<String, FactionBadges>();

    public FactionBadges getBadges(String factionId) {
        FactionBadges badges = factionBadges.get(factionId);
        if (badges == null) {
            badges = new FactionBadges(factionId);
            factionBadges.put(factionId, badges);
        }
        return badges;
    }

    public void advanceAllDecay(float days) {
        for (FactionBadges fb : factionBadges.values()) {
            fb.advanceDecay(days);
        }
    }

    /** Record that a faction broke a pact. */
    public void recordPactBroken(String factionId) {
        getBadges(factionId).earnBadge(BadgeType.OATHBREAKER);
        log.info("[Nex4x] " + factionId + " earned OATHBREAKER badge");
    }

    /** Record a war declaration. Track toward WARMONGER. */
    public void recordWarDeclared(String factionId) {
        boolean earned = getBadges(factionId).incrementProgress(BadgeType.WARMONGER, 3);
        if (earned) log.info("[Nex4x] " + factionId + " earned WARMONGER badge");
    }

    /** Record honoring a defensive pact. */
    public void recordPactHonored(String factionId) {
        getBadges(factionId).earnBadge(BadgeType.RELIABLE_PARTNER);
        log.info("[Nex4x] " + factionId + " earned RELIABLE_PARTNER badge");
    }

    /** Record an unjustified war. */
    public void recordAggression(String factionId) {
        getBadges(factionId).earnBadge(BadgeType.AGGRESSOR);
        log.info("[Nex4x] " + factionId + " earned AGGRESSOR badge");
    }

    /** Record a contract theft. */
    public void recordContractTheft(String factionId) {
        getBadges(factionId).earnBadge(BadgeType.BETRAYER);
        log.info("[Nex4x] " + factionId + " earned BETRAYER badge");
    }

    /** Record a peace negotiation. Track toward PEACEMAKER. */
    public void recordPeaceNegotiated(String factionId) {
        boolean earned = getBadges(factionId).incrementProgress(BadgeType.PEACEMAKER, 3);
        if (earned) log.info("[Nex4x] " + factionId + " earned PEACEMAKER badge");
    }
}
