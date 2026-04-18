package nex4x.leaders;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Serializable wrapper around a faction leader's PersonAPI. */
public class LeaderProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String factionId;
    private String personApiId;
    private Personality personality;
    private final List<String> traits = new ArrayList<String>();
    private int tenureDays;

    private String syntheticName;
    private String syntheticPortrait;

    private transient PersonAPI resolved;

    public LeaderProfile(String factionId, Personality personality) {
        this.factionId = factionId;
        this.personality = personality == null ? Personality.PRAGMATIC : personality;
    }

    public String getFactionId() { return factionId; }
    public String getPersonApiId() { return personApiId; }
    public void setPersonApiId(String id) { this.personApiId = id; }
    public Personality getPersonality() { return personality; }
    public void setPersonality(Personality p) { this.personality = p; }
    public List<String> getTraits() { return traits; }
    public int getTenureDays() { return tenureDays; }
    public void incrementTenure(float days) { this.tenureDays += Math.round(days); }
    public void resetTenure() { this.tenureDays = 0; }

    public String getSyntheticName() { return syntheticName; }
    public void setSyntheticName(String s) { this.syntheticName = s; }
    public String getSyntheticPortrait() { return syntheticPortrait; }
    public void setSyntheticPortrait(String s) { this.syntheticPortrait = s; }

    public PersonAPI resolve() {
        if (resolved != null) return resolved;
        if (personApiId == null) return null;
        PersonAPI p = Global.getSector().getImportantPeople().getPerson(personApiId);
        if (p != null) {
            resolved = p;
        }
        return p;
    }

    public String displayName() {
        PersonAPI p = resolve();
        if (p != null) return p.getNameString();
        if (syntheticName != null) return syntheticName;
        FactionAPI f = Global.getSector().getFaction(factionId);
        return f != null ? f.getDisplayName() + " Leader" : factionId + " Leader";
    }

    public String portraitSprite() {
        PersonAPI p = resolve();
        if (p != null) return p.getPortraitSprite();
        if (syntheticPortrait != null) return syntheticPortrait;
        return Global.getSettings().getSpriteName("characters", "default_male01");
    }

    public String titleString() {
        PersonAPI p = resolve();
        if (p != null && p.getPost() != null) return p.getPost();
        return "factionLeader";
    }
}
