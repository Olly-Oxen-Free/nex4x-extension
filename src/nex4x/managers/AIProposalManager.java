package nex4x.managers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import nex4x.agreements.AgreementType;
import nex4x.data.Nex4xSettings;
import nex4x.evaluation.DealEvaluator;
import nex4x.evaluation.DesperationCalculator;
import nex4x.negotiation.AutoNegotiator;
import nex4x.negotiation.DealPackage;
import nex4x.ui.AIProposalIntel;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages AI-initiated deal proposals to the player (spec §5.2.3).
 * Tracks per-faction cooldowns and generates proposals on daily tick.
 *
 * Cooldown: 60 days between proposals from the same faction (configurable).
 * Reduced to 30 days if the faction's desperation is above 60.
 */
public class AIProposalManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = Global.getLogger(AIProposalManager.class);

    /** Last day a proposal was sent, keyed by faction ID. */
    private final Map<String, Float> lastProposalDay = new HashMap<String, Float>();

    /** First campaign clock tick seen; used with {@link Nex4xSettings#aiProposalMinGameDays}. */
    private long firstProposalClockTimestamp;

    /**
     * Called once per day from Nex4xManager's daily tick.
     * Iterates all live factions and attempts to generate proposals for the player.
     */
    public void advanceDay() {
        String playerFactionId = Global.getSector().getPlayerFaction().getId();

        if (firstProposalClockTimestamp == 0L) {
            firstProposalClockTimestamp = Global.getSector().getClock().getTimestamp();
        }
        float daysSinceFirstTick = Global.getSector().getClock()
                .getElapsedDaysSince(firstProposalClockTimestamp);
        if (daysSinceFirstTick < Nex4xSettings.aiProposalMinGameDays) {
            return;
        }

        if (Nex4xSettings.aiProposalRequirePlayerMarket && !hasMarkets(playerFactionId)) {
            return;
        }

        // Use sector day count for simpler math
        float dayNum = Global.getSector().getClock().getDay()
                + (Global.getSector().getClock().getCycle() - 206) * 365f
                + Global.getSector().getClock().getMonth() * 30f;

        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            String fid = faction.getId();
            if (fid.equals(playerFactionId)) continue;
            if (faction.isNeutralFaction()) continue;
            if (faction.isPlayerFaction()) continue;
            if (!hasMarkets(fid)) continue;

            // Check cooldown
            float cooldown = getCooldown(fid);
            Float lastDay = lastProposalDay.get(fid);
            if (lastDay != null && (dayNum - lastDay) < cooldown) {
                continue;
            }

            // Try to generate a proposal
            DealPackage proposal = generateProposal(fid, playerFactionId);
            if (proposal == null) continue;

            try {
                AIProposalIntel intel = new AIProposalIntel(fid, proposal);
                intel.init();
            } catch (Throwable t) {
                log.error("[Nex4x] Could not create AIProposalIntel for " + fid + ": " + t.getMessage(), t);
                continue;
            }

            lastProposalDay.put(fid, dayNum);
            log.info("[Nex4x] " + fid + " proposed a deal to the player");
        }
    }

    private DealPackage generateProposal(String aiFactionId, String playerFactionId) {
        Nex4xManager mgr = Nex4xManager.getManager();
        if (mgr == null) return null;

        AgreementType currentTier = mgr.getAgreementManager()
                .getAllianceTier(aiFactionId, playerFactionId);

        DealEvaluator evaluator = new DealEvaluator();
        AutoNegotiator auto = new AutoNegotiator(evaluator);
        return auto.generateAIProposal(aiFactionId, playerFactionId, currentTier);
    }

    private float getCooldown(String factionId) {
        float desperation = DesperationCalculator.calculate(factionId);
        if (desperation > 60) {
            return Nex4xSettings.aiProposalUrgentCooldownDays;
        }
        return Nex4xSettings.aiProposalCooldownDays;
    }

    private boolean hasMarkets(String factionId) {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId())) return true;
        }
        return false;
    }
}
