package nex4x.data;

import java.util.HashSet;
import java.util.Set;

public class MemoryTypeDef {
    public final String id;
    public final String name;
    public final float baseImpact;
    public final float baseDecayDays;
    public final Set<String> amplifyingCategories;

    public MemoryTypeDef(String id, String name, float baseImpact, float baseDecayDays,
                         Set<String> amplifyingCategories) {
        this.id = id;
        this.name = name;
        this.baseImpact = baseImpact;
        this.baseDecayDays = baseDecayDays;
        this.amplifyingCategories = amplifyingCategories;
    }
}
