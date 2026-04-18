package nex4x.leaders;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LeaderRegistry implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = Global.getLogger(LeaderRegistry.class);

    private final Map<String, LeaderProfile> profiles = new HashMap<String, LeaderProfile>();

    public LeaderProfile getProfile(String factionId) {
        LeaderProfile p = profiles.get(factionId);
        if (p == null) {
            p = seed(factionId);
            profiles.put(factionId, p);
        }
        return p;
    }

    LeaderProfile seed(String factionId) {
        FactionAPI f = Global.getSector().getFaction(factionId);
        
        // Try to find existing faction leader in ImportantPeople
        PersonAPI factionLeader = null;
        for (PersonAPI person : Global.getSector().getImportantPeople().getPeopleWithPost("factionLeader")) {
            if (factionId.equals(person.getFaction())) {
                factionLeader = person;
                break;
            }
        }
        
        Personality personality = rollPersonality(factionId,
                factionLeader != null ? factionLeader.getId() : "synth");
        LeaderProfile prof = new LeaderProfile(factionId, personality);
        
        if (factionLeader != null) {
            prof.setPersonApiId(factionLeader.getId());
        } else {
            prof.setSyntheticName(f != null ? f.getDisplayName() + " Leader" : factionId + " Leader");
            prof.setSyntheticPortrait(null);
        }
        
        log.info("[Nex4x] Seeded LeaderProfile for " + factionId
                + " — personality=" + personality
                + " personApiId=" + prof.getPersonApiId());
        return prof;
    }

    Personality rollPersonality(String factionId, String personId) {
        long seed = ((long) factionId.hashCode() << 32) ^ (long) personId.hashCode();
        Random r = new Random(seed);
        Personality[] all = Personality.values();
        return all[r.nextInt(all.length)];
    }

    public List<LeaderChangeEvent> advanceDay(float days) {
        List<LeaderChangeEvent> changes = new ArrayList<LeaderChangeEvent>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (f.isNeutralFaction() || f.isPlayerFaction()) continue;
            String fid = f.getId();
            LeaderProfile existing = profiles.get(fid);
            if (existing == null) continue;
            existing.incrementTenure(days);

            // Find current faction leader
            PersonAPI currentLeader = null;
            for (PersonAPI person : Global.getSector().getImportantPeople().getPeopleWithPost("factionLeader")) {
                if (fid.equals(person.getFaction())) {
                    currentLeader = person;
                    break;
                }
            }
            
            String currentHolderId = currentLeader != null ? currentLeader.getId() : null;
            String oldId = existing.getPersonApiId();
            boolean changed = (oldId == null && currentHolderId != null)
                    || (oldId != null && !oldId.equals(currentHolderId));
            if (changed) {
                LeaderChangeEvent evt = new LeaderChangeEvent(fid, existing, oldId, currentHolderId);
                LeaderProfile fresh = seed(fid);
                profiles.put(fid, fresh);
                evt.setNewProfile(fresh);
                changes.add(evt);
                log.info("[Nex4x] Leader change detected for " + fid
                        + " old=" + oldId + " new=" + currentHolderId);
            }
        }
        return changes;
    }

    public Map<String, LeaderProfile> getAllProfiles() { return profiles; }

    public static class LeaderChangeEvent {
        public final String factionId;
        public final LeaderProfile oldProfile;
        public final String oldPersonId;
        public final String newPersonId;
        public LeaderProfile newProfile;

        public LeaderChangeEvent(String factionId, LeaderProfile oldProfile,
                                 String oldPersonId, String newPersonId) {
            this.factionId = factionId;
            this.oldProfile = oldProfile;
            this.oldPersonId = oldPersonId;
            this.newPersonId = newPersonId;
        }
        public void setNewProfile(LeaderProfile p) { this.newProfile = p; }
    }
}
