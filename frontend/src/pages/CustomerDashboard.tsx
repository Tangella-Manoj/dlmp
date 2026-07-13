import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Plus, Wallet } from "lucide-react";
import { loansApi } from "@/api/loans";
import { LoanCard } from "@/components/loans/LoanCard";
import { Button } from "@/components/ui/Button";
import { PageSpinner } from "@/components/ui/Spinner";
import { EmptyState } from "@/components/ui/EmptyState";
import { Card, CardBody } from "@/components/ui/Card";
import { formatCurrency } from "@/lib/format";
import { useAuth } from "@/context/AuthContext";

export function CustomerDashboard() {
  const { session } = useAuth();
  const { data: page, isLoading } = useQuery({
    queryKey: ["loans", "my"],
    queryFn: () => loansApi.my(0, 50),
  });

  const loans = page?.content ?? [];
  const active = loans.filter((l) => l.status === "ACTIVE");
  const totalOutstanding = active.reduce((sum, l) => sum + (l.outstandingPrincipal ?? 0), 0);
  const totalEmi = active.reduce((sum, l) => sum + (l.emiAmount ?? 0), 0);

  if (isLoading) return <PageSpinner />;

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-bold text-ink-900">
            Welcome back, {session?.firstName}
          </h1>
          <p className="mt-1 text-sm text-ink-500">Here's an overview of your loans.</p>
        </div>
        <Link to="/loans/apply">
          <Button size="lg">
            <Plus className="size-4" /> Apply for a Loan
          </Button>
        </Link>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard label="Active Loans" value={String(active.length)} />
        <StatCard label="Monthly EMI" value={formatCurrency(totalEmi)} />
        <StatCard label="Total Outstanding" value={formatCurrency(totalOutstanding)} />
      </div>

      <div>
        <h2 className="mb-4 text-lg font-semibold text-ink-900">My Loans</h2>
        {loans.length === 0 ? (
          <EmptyState
            icon={Wallet}
            title="No loans yet"
            description="Apply for your first loan to get an instant credit decision."
            action={
              <Link to="/loans/apply">
                <Button>
                  <Plus className="size-4" /> Apply Now
                </Button>
              </Link>
            }
          />
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {loans.map((loan) => (
              <LoanCard key={loan.id} loan={loan} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <CardBody>
        <p className="text-sm text-ink-500">{label}</p>
        <p className="mt-1.5 font-display text-2xl font-bold text-ink-900">{value}</p>
      </CardBody>
    </Card>
  );
}
