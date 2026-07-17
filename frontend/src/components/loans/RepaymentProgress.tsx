import { formatCurrency } from "@/lib/format";

interface RepaymentProgressProps {
  sanctionedAmount?: number;
  outstandingPrincipal?: number;
  paidInstallments: number;
  totalInstallments: number;
}

export function RepaymentProgress({
  sanctionedAmount,
  outstandingPrincipal,
  paidInstallments,
  totalInstallments,
}: RepaymentProgressProps) {
  if (!sanctionedAmount || sanctionedAmount <= 0) return null;
  const repaid = Math.max(0, sanctionedAmount - (outstandingPrincipal ?? sanctionedAmount));
  const pct = Math.min(100, Math.round((repaid / sanctionedAmount) * 100));

  return (
    <div>
      <div className="flex items-center justify-between text-sm">
        <p className="font-medium text-ink-700">Repayment progress</p>
        <p className="text-ink-500">
          {paidInstallments} of {totalInstallments} EMIs paid
        </p>
      </div>
      <div className="mt-2 h-2.5 w-full overflow-hidden rounded-full bg-ink-100">
        <div
          className="h-full rounded-full bg-gradient-to-r from-success-500 to-success-600 transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
      <div className="mt-1.5 flex items-center justify-between text-xs text-ink-400">
        <span>{formatCurrency(repaid)} repaid</span>
        <span>{pct}%</span>
        <span>{formatCurrency(sanctionedAmount)} total</span>
      </div>
    </div>
  );
}
