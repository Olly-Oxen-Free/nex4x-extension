package nex4x.influence;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

/** Per-faction ledger tracking influence balance and per-source income/expense. */
public class InfluenceLedger implements Serializable {
    private static final long serialVersionUID = 1L;

    private float balance;
    private final EnumMap<InfluenceSource, Float> incomeBreakdown =
            new EnumMap<InfluenceSource, Float>(InfluenceSource.class);
    private final EnumMap<InfluenceSource, Float> expenseBreakdown =
            new EnumMap<InfluenceSource, Float>(InfluenceSource.class);

    public float getBalance() { return balance; }
    public boolean canAfford(float amount) { return balance >= amount; }

    public boolean spend(float amount, InfluenceSource src) {
        if (balance < amount) return false;
        balance -= amount;
        addExpense(src, amount);
        return true;
    }

    public void addLump(float amount, InfluenceSource src) {
        if (amount <= 0) return;
        balance += amount;
        addIncome(src, amount);
    }

    public void addIncome(InfluenceSource src, float amount) {
        Float v = incomeBreakdown.get(src);
        incomeBreakdown.put(src, (v == null ? 0f : v) + amount);
    }

    public void addExpense(InfluenceSource src, float amount) {
        Float v = expenseBreakdown.get(src);
        expenseBreakdown.put(src, (v == null ? 0f : v) + amount);
    }

    public void applyCycleIncome(float amount, InfluenceSource src) {
        balance += amount;
        addIncome(src, amount);
    }

    public void applyCycleExpense(float amount, InfluenceSource src) {
        balance -= amount;
        addExpense(src, amount);
        if (balance < 0) balance = 0;
    }

    /** Reset per-cycle breakdown views. */
    public void resetBreakdown() {
        incomeBreakdown.clear();
        expenseBreakdown.clear();
    }

    public Map<InfluenceSource, Float> getIncomeBreakdown() { return incomeBreakdown; }
    public Map<InfluenceSource, Float> getExpenseBreakdown() { return expenseBreakdown; }
}
