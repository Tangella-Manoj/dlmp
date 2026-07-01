package com.dlmp.loan.domain.enums;

public enum LoanType {
    PERSONAL(0.1400, 60,    10_000,   5_000_000),
    HOME    (0.0875, 300,  500_000,  50_000_000),
    VEHICLE (0.1050, 84,  100_000,  10_000_000),
    BUSINESS(0.1500, 120,  50_000,  25_000_000),
    EDUCATION(0.0950,84,   50_000,  20_000_000),
    GOLD    (0.0850, 36,   10_000,   5_000_000);

    private final double annualRate;
    private final int maxTenureMonths;
    private final long minAmount;
    private final long maxAmount;

    LoanType(double annualRate, int maxTenureMonths, long minAmount, long maxAmount) {
        this.annualRate = annualRate;
        this.maxTenureMonths = maxTenureMonths;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    public double getAnnualRate() { return annualRate; }
    public int getMaxTenureMonths() { return maxTenureMonths; }
    public long getMinAmount() { return minAmount; }
    public long getMaxAmount() { return maxAmount; }
}
