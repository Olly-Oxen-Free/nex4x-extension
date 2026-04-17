package nex4x.data;

import java.awt.Color;

public enum TendencyId {
    MILITARISTS("Militarists", "Strength through force", new Color(200, 50, 50)),
    FEDERALISTS("Federalists", "Strength through cooperation", new Color(50, 150, 255)),
    ZEALOTS("Zealots", "Purity above pragmatism", new Color(200, 150, 50)),
    CORPORATISTS("Corporatists", "Profit above principle", new Color(50, 200, 50)),
    INDUSTRIALISTS("Industrialists", "Production and self-sufficiency", new Color(180, 130, 80)),
    ECOLOGISTS("Ecologists", "Sustainable growth", new Color(100, 200, 100));

    public final String displayName;
    public final String description;
    public final Color color;

    TendencyId(String displayName, String description, Color color) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    public TendencyId getOpposite() {
        switch (this) {
            case MILITARISTS: return FEDERALISTS;
            case FEDERALISTS: return MILITARISTS;
            case ZEALOTS: return CORPORATISTS;
            case CORPORATISTS: return ZEALOTS;
            case INDUSTRIALISTS: return ECOLOGISTS;
            case ECOLOGISTS: return INDUSTRIALISTS;
            default: throw new IllegalStateException();
        }
    }
}
