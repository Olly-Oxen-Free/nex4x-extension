package nex4x.agents;

import exerelin.campaign.intel.agents.AgentIntel;

/**
 * Bidirectional mapping between nex4x {@link AgentType} and Nex {@link AgentIntel.Specialization}.
 *
 * Nex owns the canonical specialization (set on agent recruit / training); nex4x's AgentType
 * is a tracking convenience that shadows the Nex value. Always derive AgentType from
 * Specialization at sighting; never invent it.
 */
public final class AgentTypeMap {

    private AgentTypeMap() {}

    public static AgentType fromSpecialization(AgentIntel.Specialization spec) {
        if (spec == null) return AgentType.COVERT;
        switch (spec) {
            case SABOTEUR:   return AgentType.COVERT;
            case HYBRID:     return AgentType.GUERRILLA;
            case NEGOTIATOR: return AgentType.DIPLOMAT;
            default:         return AgentType.COVERT;
        }
    }

    public static AgentIntel.Specialization toSpecialization(AgentType type) {
        if (type == null) return AgentIntel.Specialization.SABOTEUR;
        switch (type) {
            case COVERT:    return AgentIntel.Specialization.SABOTEUR;
            case GUERRILLA: return AgentIntel.Specialization.HYBRID;
            case DIPLOMAT:  return AgentIntel.Specialization.NEGOTIATOR;
            default:        return AgentIntel.Specialization.SABOTEUR;
        }
    }

    /**
     * Resolve type from a live AgentIntel; null-safe, falls back to COVERT.
     * AgentIntel can hold multiple specializations; pick by priority NEGOTIATOR &gt; HYBRID &gt; SABOTEUR
     * so a hybrid-trained negotiator still classifies as DIPLOMAT.
     */
    public static AgentType fromAgent(AgentIntel agent) {
        if (agent == null) return AgentType.COVERT;
        try {
            java.util.Set<AgentIntel.Specialization> specs = agent.getSpecializationsCopy();
            if (specs == null || specs.isEmpty()) return AgentType.COVERT;
            if (specs.contains(AgentIntel.Specialization.NEGOTIATOR)) return AgentType.DIPLOMAT;
            if (specs.contains(AgentIntel.Specialization.HYBRID))     return AgentType.GUERRILLA;
            if (specs.contains(AgentIntel.Specialization.SABOTEUR))   return AgentType.COVERT;
            return AgentType.COVERT;
        } catch (Throwable t) {
            return AgentType.COVERT;
        }
    }
}
