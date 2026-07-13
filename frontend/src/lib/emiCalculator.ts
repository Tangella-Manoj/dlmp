// Mirrors loan-service's EmiCalculatorService reducing-balance formula, for
// instant client-side preview only — the backend value is authoritative.
export function estimateEmi(principal: number, annualRatePct: number, tenureMonths: number): number {
  if (!principal || !tenureMonths || principal <= 0 || tenureMonths <= 0) return 0;
  const r = annualRatePct / 100 / 12;
  if (r === 0) return principal / tenureMonths;
  const pow = Math.pow(1 + r, tenureMonths);
  return (principal * r * pow) / (pow - 1);
}

export function estimateTotalInterest(emi: number, months: number, principal: number): number {
  return emi * months - principal;
}
