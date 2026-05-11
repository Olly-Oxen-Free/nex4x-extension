package nex4x.negotiation;

import nex4x.leaders.Personality;
import nex4x.leaders.ReputationTier;

/** Ephemeral per-session mood tracker. Not serialized. */
public class SessionMood {

    public enum Event {
        AGGRESSIVE_DEMAND(-1),
        CONCESSION(+1),
        INSULT(-2),
        PRAISE(+1),
        REPEAT_AFTER_REJECT(-1),
        AUTO_BALANCE(+1);

        public final int delta;
        Event(int d) { this.delta = d; }
    }

    private int delta = 0;

    public void apply(Event e, Personality personality) {
        int step = e.delta;
        if (personality == Personality.PARANOID && step > 0) step = 0;
        if (personality == Personality.GENIAL && step < 0) step = Math.max(step / 2, -1);
        delta += step;
        if (delta > 4)  delta = 4;
        if (delta < -4) delta = -4;
    }

    public int getDelta() { return delta; }

    public int tierShift() {
        if (delta >= 2)  return +1;
        if (delta <= -2) return -1;
        return 0;
    }

    public float thresholdDeltaPct() {
        return -0.02f * (float) delta;
    }

    public ReputationTier effectiveTier(ReputationTier baseTier) {
        return ReputationTier.shift(baseTier, tierShift());
    }

    public void reset() { delta = 0; }
}
