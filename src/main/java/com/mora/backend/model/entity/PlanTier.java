package com.mora.backend.model.entity;

/** Product entitlements. Prices intentionally live outside this enum until billing is enabled. */
public enum PlanTier {
    FREE(2, 300_000L, "Miễn phí"),
    PRO(10, 3_000_000L, "Pro"),
    TEAM(25, 12_000_000L, "Team");

    private final int maxOwnedSpaces;
    private final long monthlyTokenLimit;
    private final String displayName;
    PlanTier(int maxOwnedSpaces, long monthlyTokenLimit, String displayName) {
        this.maxOwnedSpaces = maxOwnedSpaces;
        this.monthlyTokenLimit = monthlyTokenLimit;
        this.displayName = displayName;
    }
    public int getMaxOwnedSpaces() { return maxOwnedSpaces; }
    public long getMonthlyTokenLimit() { return monthlyTokenLimit; }
    public String getDisplayName() { return displayName; }
}
