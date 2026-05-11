package nex4x.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FactionBeliefs implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Visibility { PUBLIC, SECRET }

    public static class BeliefEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        public final String beliefId;
        public final int strength;      // 1-3
        public final Visibility visibility;

        public BeliefEntry(String beliefId, int strength, String visibilityStr) {
            this.beliefId = beliefId;
            this.strength = Math.max(1, Math.min(3, strength));
            this.visibility = Visibility.valueOf(visibilityStr.toUpperCase());
        }

        public float getImpactMultiplier() {
            switch (strength) {
                case 1: return Nex4xSettings.beliefStr1ImpactMult;
                case 2: return Nex4xSettings.beliefStr2ImpactMult;
                case 3: return Nex4xSettings.beliefStr3ImpactMult;
                default: return 1f;
            }
        }

        public float getDecayModifier() {
            switch (strength) {
                case 1: return Nex4xSettings.beliefStr1DecayMod;
                case 2: return Nex4xSettings.beliefStr2DecayMod;
                case 3: return Nex4xSettings.beliefStr3DecayMod;
                default: return 1f;
            }
        }
    }

    private final String factionId;
    private final List<BeliefEntry> entries;

    public FactionBeliefs(String factionId, List<BeliefEntry> entries) {
        this.factionId = factionId;
        this.entries = new ArrayList<BeliefEntry>(entries);
    }

    public String getFactionId() { return factionId; }
    public List<BeliefEntry> getEntries() { return entries; }

    public List<BeliefEntry> getByCategory(BeliefDef.Category category) {
        List<BeliefEntry> result = new ArrayList<BeliefEntry>();
        for (BeliefEntry e : entries) {
            BeliefDef def = BeliefRegistry.get(e.beliefId);
            if (def != null && def.category == category) {
                result.add(e);
            }
        }
        return result;
    }

    public BeliefEntry getById(String beliefId) {
        for (BeliefEntry e : entries) {
            if (e.beliefId.equals(beliefId)) return e;
        }
        return null;
    }

    public BeliefEntry getStrongestInCategory(BeliefDef.Category category) {
        BeliefEntry best = null;
        for (BeliefEntry e : entries) {
            BeliefDef def = BeliefRegistry.get(e.beliefId);
            if (def != null && def.category == category) {
                if (best == null || e.strength > best.strength) {
                    best = e;
                }
            }
        }
        return best;
    }
}
