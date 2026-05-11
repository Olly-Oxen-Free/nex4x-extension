package nex4x.peace;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Terms proposed or ratified in a peace conference. */
public class PeaceTerms implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum TermType { CEASEFIRE, TERRITORY_CEDE, REPARATIONS, NAP, WHITE_PEACE, TRADE_RESUMPTION, WAR_REPARATIONS }

    public static class Term implements Serializable {
        private static final long serialVersionUID = 1L;
        public final TermType type;
        public final String payload;       // e.g. market ID ceded
        public final float amount;         // e.g. credits for reparations
        public Term(TermType type, String payload, float amount) {
            this.type = type; this.payload = payload; this.amount = amount;
        }
    }

    private final List<Term> terms = new ArrayList<Term>();

    public void add(Term t) { terms.add(t); }
    public List<Term> getTerms() { return terms; }
    public boolean isEmpty() { return terms.isEmpty(); }
}
