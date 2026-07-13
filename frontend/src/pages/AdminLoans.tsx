import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { FileSearch } from "lucide-react";
import { loansApi } from "@/api/loans";
import { Card } from "@/components/ui/Card";
import { LoanStatusBadge } from "@/components/ui/Badge";
import { PageSpinner } from "@/components/ui/Spinner";
import { EmptyState } from "@/components/ui/EmptyState";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils";
import { formatCurrency, formatDate } from "@/lib/format";
import { LOAN_TYPE_LABELS } from "@/lib/loanMeta";
import type { LoanStatus } from "@/types/domain";

const FILTERS: { label: string; value: LoanStatus | "ALL" }[] = [
  { label: "All", value: "ALL" },
  { label: "Pending Review", value: "PENDING_REVIEW" },
  { label: "Approved", value: "APPROVED" },
  { label: "Active", value: "ACTIVE" },
  { label: "Rejected", value: "REJECTED" },
  { label: "Closed", value: "CLOSED" },
];

export function AdminLoansPage() {
  const [filter, setFilter] = useState<LoanStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["loans", "admin-list", filter, page],
    queryFn: () => loansApi.list(filter === "ALL" ? undefined : filter, page, 20),
  });

  const loans = data?.content ?? [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-2xl font-bold text-ink-900">Loan Applications</h1>
        <p className="mt-1 text-sm text-ink-500">Review, approve, and disburse loans.</p>
      </div>

      <div className="flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => {
              setFilter(f.value);
              setPage(0);
            }}
            className={cn(
              "rounded-lg px-3.5 py-2 text-sm font-medium transition-colors",
              filter === f.value ? "bg-ink-900 text-white" : "bg-white text-ink-600 ring-1 ring-ink-200 hover:bg-ink-50",
            )}
          >
            {f.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <PageSpinner />
      ) : loans.length === 0 ? (
        <EmptyState icon={FileSearch} title="No loans found" description="Try a different filter." />
      ) : (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto scrollbar-thin">
            <table className="w-full text-left text-sm">
              <thead className="bg-ink-50">
                <tr className="text-xs uppercase tracking-wide text-ink-400">
                  <th className="px-5 py-3 font-medium">Loan #</th>
                  <th className="px-5 py-3 font-medium">Type</th>
                  <th className="px-5 py-3 font-medium">Amount</th>
                  <th className="px-5 py-3 font-medium">Credit Score</th>
                  <th className="px-5 py-3 font-medium">Applied</th>
                  <th className="px-5 py-3 font-medium">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100">
                {loans.map((loan) => (
                  <tr key={loan.id} className="hover:bg-ink-50">
                    <td className="px-5 py-3.5">
                      <Link to={`/loans/${loan.id}`} className="font-mono text-xs font-medium text-brand-600 hover:underline">
                        {loan.loanNumber}
                      </Link>
                    </td>
                    <td className="px-5 py-3.5 text-ink-700">{LOAN_TYPE_LABELS[loan.loanType]}</td>
                    <td className="px-5 py-3.5 font-medium text-ink-900">
                      {formatCurrency(loan.principalAmount)}
                    </td>
                    <td className="px-5 py-3.5 text-ink-600">{loan.creditScore ?? "—"}</td>
                    <td className="px-5 py-3.5 text-ink-500">{formatDate(loan.createdAt)}</td>
                    <td className="px-5 py-3.5">
                      <LoanStatusBadge status={loan.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <Button variant="outline" size="sm" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <span className="text-sm text-ink-500">
            Page {data.number + 1} of {data.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
