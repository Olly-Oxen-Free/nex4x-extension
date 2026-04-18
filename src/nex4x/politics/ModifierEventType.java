package nex4x.politics;

import nex4x.data.TendencyId;

/**
 * 18 event types that shift faction tendency bars.
 * Each maps to a target tendency, a base amount, and a decay rate.
 */
public enum ModifierEventType {
    BATTLE_WON(TendencyId.MILITARISTS, 2.0f, 0.033f, "Won battle"),
    BATTLE_LOST(TendencyId.FEDERALISTS, 2.0f, 0.033f, "Lost battle"),
    MARKET_CAPTURED(TendencyId.MILITARISTS, 3.0f, 0.025f, "Captured market"),
    MARKET_LOST(TendencyId.FEDERALISTS, 3.0f, 0.025f, "Lost market"),
    SAT_BOMBARDMENT_DONE(TendencyId.MILITARISTS, 1.5f, 0.05f, "Bombarded a world"),
    SAT_BOMBARDMENT_RECEIVED(TendencyId.ZEALOTS, 2.5f, 0.033f, "Bombarded by enemy"),
    TRADE_PROSPERITY(TendencyId.CORPORATISTS, 2.0f, 0.033f, "Trade prosperity"),
    ECONOMIC_CRISIS(TendencyId.INDUSTRIALISTS, 2.0f, 0.033f, "Economic downturn"),
    EMBARGO_SUFFERED(TendencyId.MILITARISTS, 1.5f, 0.05f, "Suffered embargo"),
    TRADE_DEAL_SIGNED(TendencyId.CORPORATISTS, 1.0f, 0.05f, "Signed trade deal"),
    ALLIANCE_FORMED(TendencyId.FEDERALISTS, 2.0f, 0.033f, "Formed alliance"),
    AGREEMENT_BROKEN_BY_US(TendencyId.MILITARISTS, 1.5f, 0.05f, "Broke agreement"),
    AGREEMENT_BROKEN_BY_THEM(TendencyId.ZEALOTS, 2.0f, 0.033f, "Agreement broken by partner"),
    PEACE_SIGNED(TendencyId.FEDERALISTS, 1.5f, 0.05f, "Signed peace"),
    IDEOLOGY_SPREAD(TendencyId.ZEALOTS, 1.5f, 0.05f, "Ideology spread"),
    IDEOLOGY_CHALLENGED(TendencyId.ZEALOTS, 2.0f, 0.033f, "Ideology challenged"),
    ESPIONAGE_CAUGHT(TendencyId.MILITARISTS, 1.0f, 0.05f, "Caught spy"),
    STABILITY_CRISIS(TendencyId.INDUSTRIALISTS, 2.5f, 0.033f, "Stability crisis");

    public final TendencyId targetTendency;
    public final float baseAmount;
    public final float decayPerDay;
    public final String displayName;

    ModifierEventType(TendencyId targetTendency, float baseAmount,
                      float decayPerDay, String displayName) {
        this.targetTendency = targetTendency;
        this.baseAmount = baseAmount;
        this.decayPerDay = decayPerDay;
        this.displayName = displayName;
    }
}
