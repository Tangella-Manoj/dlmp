import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { BarChart3, PiggyBank, TrendingUp, Wallet } from "lucide-react";
import { reportsApi } from "@/api/reports";
import { Card, CardBody } from "@/components/ui/Card";
import { PageSpinner } from "@/components/ui/Spinner";
import { CashFlowChart, StatusDistributionChart } from "@/components/charts/PortfolioCharts";
import { formatCurrency } from "@/lib/format";
import { LOAN_STATUSES } from "@/types/domain";

export function AdminReportsPage() {
  const portfolioQuery = useQuery({
    queryKey: ["reports", "portfolio"],
    queryFn: reportsApi.portfolio,
  });

  const loansQuery = useQuery({
    queryKey: ["reports", "loans", "all"],
    queryFn: () => reportsApi.loans(undefined, 0, 200),
  });

  const statusCounts = useMemo(() => {
    const counts: Record<string, number> = Object.fromEntries(LOAN_STATUSES.map((s) => [s, 0]));
    for (const loan of loansQuery.data?.content ?? []) {
      counts[loan.currentStatus] = (counts[loan.currentStatus] ?? 0) + 1;
    }
    return counts;
  }, [loansQuery.data]);

  if (portfolioQuery.isLoading) return <PageSpinner />;

  const p = portfolioQuery.data;
  const outstanding = Math.max((p?.totalDisbursed ?? 0) - (p?.totalRecovered ?? 0), 0);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="flex items-center gap-2 font-display text-2xl font-bold text-ink-900">
          <BarChart3 className="size-6 text-brand-600" /> Portfolio Reports
        </h1>
        <p className="mt-1 text-sm text-ink-500">
          Live, event-driven analytics materialized from the Kafka event stream.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat icon={Wallet} label="Total Loans" value={String(p?.totalLoans ?? 0)} />
        <Stat icon={TrendingUp} label="Active Loans" value={String(p?.activeLoans ?? 0)} />
        <Stat icon={PiggyBank} label="Total Disbursed" value={formatCurrency(p?.totalDisbursed)} />
        <Stat icon={PiggyBank} label="Total Recovered" value={formatCurrency(p?.totalRecovered)} accent="success" />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <CashFlowChart
          disbursed={p?.totalDisbursed ?? 0}
          recovered={p?.totalRecovered ?? 0}
          outstanding={outstanding}
        />
        <StatusDistributionChart counts={statusCounts} />
      </div>
    </div>
  );
}

function Stat({
  icon: Icon,
  label,
  value,
  accent = "brand",
}: {
  icon: typeof Wallet;
  label: string;
  value: string;
  accent?: "brand" | "success";
}) {
  return (
    <Card>
      <CardBody className="flex items-center gap-3">
        <div
          className={
            accent === "success"
              ? "flex size-10 shrink-0 items-center justify-center rounded-lg bg-success-50"
              : "flex size-10 shrink-0 items-center justify-center rounded-lg bg-brand-50"
          }
        >
          <Icon className={accent === "success" ? "size-5 text-success-600" : "size-5 text-brand-600"} />
        </div>
        <div>
          <p className="text-xs text-ink-500">{label}</p>
          <p className="font-display text-xl font-bold text-ink-900">{value}</p>
        </div>
      </CardBody>
    </Card>
  );
}
