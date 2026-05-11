package nex4x.memory;

import nex4x.data.Nex4xSettings;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FactionMemoryStore implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String holderFactionId;
    private final String aboutFactionId;
    private final List<FactionMemory> memories = new ArrayList<FactionMemory>();

    public FactionMemoryStore(String holderFactionId, String aboutFactionId) {
        this.holderFactionId = holderFactionId;
        this.aboutFactionId = aboutFactionId;
    }

    public void addMemory(FactionMemory memory) {
        memories.add(memory);
        pruneIfNeeded();
    }

    public void advanceDecay(float days) {
        List<FactionMemory> toRemove = new ArrayList<FactionMemory>();
        for (FactionMemory m : memories) {
            if (m.advanceDecay(days)) {
                toRemove.add(m);
            }
        }
        memories.removeAll(toRemove);
    }

    public float getTotalDispositionModifier() {
        float total = 0;
        for (FactionMemory m : memories) {
            total += m.getCurrentImpact();
        }
        return total;
    }

    public List<FactionMemory> getMemories() {
        List<FactionMemory> sorted = new ArrayList<FactionMemory>(memories);
        Collections.sort(sorted, new Comparator<FactionMemory>() {
            public int compare(FactionMemory a, FactionMemory b) {
                return Float.compare(b.getTimestamp(), a.getTimestamp());
            }
        });
        return sorted;
    }

    public int getMemoryCount() { return memories.size(); }

    private void pruneIfNeeded() {
        int cap = Nex4xSettings.maxMemoriesPerPair;
        if (memories.size() <= cap) return;

        List<FactionMemory> sorted = new ArrayList<FactionMemory>(memories);
        Collections.sort(sorted, new Comparator<FactionMemory>() {
            public int compare(FactionMemory a, FactionMemory b) {
                return Float.compare(a.getStrengthFraction(), b.getStrengthFraction());
            }
        });

        while (memories.size() > cap) {
            memories.remove(sorted.remove(0));
        }
    }

    public String getHolderFactionId() { return holderFactionId; }
    public String getAboutFactionId() { return aboutFactionId; }
}
