import { Link } from "react-router-dom";
import { ArrowRight, Calendar, Percent } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { LoanStatusBadge } from "@/components/ui/Badge";
import { formatCurrency, formatDate, formatInterestRate } from "@/lib/format";
import { LOAN_TYPE_LABELS } from "@/lib/loanMeta";
import type { LoanResponse } from "@/types/domain";

export function LoanCard({ loan }: { loan: LoanResponse }) {
  return (
    <Link to={`/loans/${loan.id}`}>
      <Card className="group cursor-pointer p-5 hover:shadow-card-hover">
        <div className="flex items-start justify-between">
          <div>
            <p className="font-mono text-xs font-medium text-ink-400">{loan.loanNumber}</p>
            <h3 className="mt-1 font-display text-lg font-bold text-ink-900">
              {LOAN_TYPE_LABELS[loan.loanType]}
            </h3>
          </div>
          <LoanStatusBadge status={loan.status} />
        </div>

        <div className="mt-4 flex items-baseline gap-1.5">
          <span className="font-display text-2xl font-bold text-ink-900">
            {formatCurrency(loan.principalAmount)}
          </span>
          {loan.emiAmount && (
            <span className="text-sm text-ink-400">· {formatCurrency(loan.emiAmount)}/mo</span>
          )}
        </div>

        <div className="mt-4 flex items-center gap-4 text-xs text-ink-500">
          <span className="flex items-center gap-1">
            <Percent className="size-3.5" /> {formatInterestRate(loan.interestRate)}
          </span>
          <span className="flex items-center gap-1">
            <Calendar className="size-3.5" /> {loan.tenureMonths} months
          </span>
        </div>

        {loan.status === "ACTIVE" && loan.outstandingPrincipal !== undefined && (
          <div className="mt-4 border-t border-ink-100 pt-3">
            <div className="flex justify-between text-xs text-ink-500">
              <span>Outstanding</span>
              <span className="font-medium text-ink-700">
                {formatCurrency(loan.outstandingPrincipal)}
              </span>
            </div>
            <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-ink-100">
              <div
                className="h-full rounded-full bg-brand-500"
                style={{
                  width: `${Math.max(
                    2,
                    100 -
                      (loan.outstandingPrincipal / (loan.sanctionedAmount || loan.principalAmount)) *
                        100,
                  )}%`,
                }}
              />
            </div>
          </div>
        )}

        <div className="mt-4 flex items-center justify-between text-xs text-ink-400">
          <span>Applied {formatDate(loan.createdAt)}</span>
          <span className="flex items-center gap-0.5 font-medium text-brand-600 opacity-0 transition-opacity group-hover:opacity-100">
            View details <ArrowRight className="size-3.5" />
          </span>
        </div>
      </Card>
    </Link>
  );
}
