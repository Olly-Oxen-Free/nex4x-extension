package nex4x.data;

public class BeliefDef {

    public enum Category {
        IDEOLOGICAL, TERRITORIAL, ECONOMIC, POLITICAL, MILITARY
    }

    public final String id;
    public final String name;
    public final Category category;
    public final String description;

    public BeliefDef(String id, String name, String categoryStr, String description) {
        this.id = id;
        this.name = name;
        this.category = Category.valueOf(categoryStr.toUpperCase());
        this.description = description;
    }
}
