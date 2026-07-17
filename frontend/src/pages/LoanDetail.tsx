import { useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { ArrowLeft, Banknote, CalendarClock, CheckCircle2, Percent, ShieldAlert, XCircle } from "lucide-react";
import { loansApi } from "@/api/loans";
import { paymentsApi } from "@/api/payments";
import { apiErrorMessage } from "@/api/client";
import { useAuth } from "@/context/AuthContext";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/Card";
import { LoanStatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { PageSpinner } from "@/components/ui/Spinner";
import { EmptyState } from "@/components/ui/EmptyState";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { EmiScheduleTable } from "@/components/loans/EmiScheduleTable";
import { PayEmiDialog } from "@/components/loans/PayEmiDialog";
import { RejectLoanDialog } from "@/components/loans/RejectLoanDialog";
import { LoanTimeline } from "@/components/loans/LoanTimeline";
import { RepaymentProgress } from "@/components/loans/RepaymentProgress";
import { formatCurrency, formatDate, formatInterestRate } from "@/lib/format";
import { LOAN_TYPE_LABELS } from "@/lib/loanMeta";

export function LoanDetailPage() {
  const { loanId } = useParams<{ loanId: string }>();
  const { session } = useAuth();
  const navigate = useNavigate();
  const qc = useQueryClient();

  const [payOpen, setPayOpen] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [disburseOpen, setDisburseOpen] = useState(false);

  const isOfficer = session?.role === "ROLE_ADMIN" || session?.role === "ROLE_LOAN_OFFICER";

  // Payments are applied to the loan asynchronously (payment-service's outbox
  // relay -> Kafka -> loan-service's consumer), typically within ~5-10s but
  // not instantly. A one-shot invalidate right after paying often refetches
  // before that's landed, showing no visible change. Polling while the loan
  // is ACTIVE (the only state where this async update can happen) closes
  // that gap without requiring a manual refresh.
  // Push (useNotificationStream) invalidates these instantly when a relevant
  // event lands for this loan's owner — polling now only needs to be a slow
  // safety net for the rare gap right after a reconnect.
  const loanQuery = useQuery({
    queryKey: ["loans", loanId],
    queryFn: () => loansApi.getById(loanId!),
    enabled: !!loanId,
    refetchInterval: (query) => (query.state.data?.status === "ACTIVE" ? 20_000 : false),
  });

  const scheduleQuery = useQuery({
    queryKey: ["loans", loanId, "schedule"],
    queryFn: () => loansApi.emiSchedule(loanId!),
    enabled: !!loanId && loanQuery.data?.status !== "PENDING_REVIEW" && loanQuery.data?.status !== "REJECTED",
    refetchInterval: loanQuery.data?.status === "ACTIVE" ? 20_000 : false,
  });

  const paymentsQuery = useQuery({
    queryKey: ["payments", "loan", loanId],
    queryFn: () => paymentsApi.byLoan(loanId!, 0, 10),
    enabled: !!loanId,
    refetchInterval: loanQuery.data?.status === "ACTIVE" ? 20_000 : false,
  });

  function invalidateAll() {
    qc.invalidateQueries({ queryKey: ["loans"] });
    qc.invalidateQueries({ queryKey: ["payments", "loan", loanId] });
    // Best-effort — report-service lags behind Kafka, so this may still
    // refetch pre-update data; AdminReports' own polling is the real fix.
    qc.invalidateQueries({ queryKey: ["reports"] });
  }

  const approveMutation = useMutation({
    mutationFn: () => loansApi.approve(loanId!),
    onSuccess: () => {
      toast.success("Loan approved");
      setApproveOpen(false);
      invalidateAll();
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });

  const rejectMutation = useMutation({
    mutationFn: (rejectionReason: string) => loansApi.reject(loanId!, { rejectionReason }),
    onSuccess: () => {
      toast.success("Loan rejected");
      setRejectOpen(false);
      invalidateAll();
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });

  const disburseMutation = useMutation({
    mutationFn: () => loansApi.disburse(loanId!),
    onSuccess: () => {
      toast.success("Loan disbursed — EMI schedule generated");
      setDisburseOpen(false);
      invalidateAll();
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });

  if (loanQuery.isLoading) return <PageSpinner />;

  if (loanQuery.isError) {
    return (
      <EmptyState
        icon={ShieldAlert}
        title="Can't load this loan"
        description={apiErrorMessage(loanQuery.error)}
        action={
          <Button variant="outline" onClick={() => navigate(-1)}>
            Go back
          </Button>
        }
      />
    );
  }

  const loan = loanQuery.data!;
  const isOwner = session?.userId === loan.userId;
  const schedule = scheduleQuery.data ?? [];
  const nextInstallment = schedule.find((e) => e.status !== "PAID");

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <Link to={isOfficer ? "/admin/loans" : "/"} className="flex items-center gap-1.5 text-sm font-medium text-ink-500 hover:text-ink-700">
        <ArrowLeft className="size-4" /> Back
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="font-mono text-sm text-ink-400">{loan.loanNumber}</p>
          <h1 className="mt-1 font-display text-2xl font-bold text-ink-900">
            {LOAN_TYPE_LABELS[loan.loanType]}
          </h1>
        </div>
        <div className="flex items-center gap-3">
          <LoanStatusBadge status={loan.status} />
        </div>
      </div>

      {!["REJECTED", "CANCELLED", "DEFAULTED", "NPA"].includes(loan.status) && (
        <Card>
          <CardBody>
            <LoanTimeline status={loan.status} />
          </CardBody>
        </Card>
      )}

      {/* Action bar */}
      {isOfficer && (loan.status === "PENDING_REVIEW" || loan.status === "UNDER_REVIEW") && (
        <Card className="border-warning-200 bg-warning-50/50">
          <CardBody className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm font-medium text-warning-800">
              This application is awaiting your decision.
            </p>
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setRejectOpen(true)}>
                <XCircle className="size-4" /> Reject
              </Button>
              <Button onClick={() => setApproveOpen(true)}>
                <CheckCircle2 className="size-4" /> Approve
              </Button>
            </div>
          </CardBody>
        </Card>
      )}
      {isOfficer && loan.status === "APPROVED" && (
        <Card className="border-info-200 bg-info-50/50">
          <CardBody className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm font-medium text-info-800">
              Approved — ready to disburse funds and generate the EMI schedule.
            </p>
            <Button onClick={() => setDisburseOpen(true)}>
              <Banknote className="size-4" /> Disburse Loan
            </Button>
          </CardBody>
        </Card>
      )}
      {isOwner && loan.status === "ACTIVE" && nextInstallment && (
        <Card className="border-brand-200 bg-brand-50/50">
          <CardBody className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-sm font-medium text-brand-800">
                Installment #{nextInstallment.installmentNumber} — {formatCurrency(nextInstallment.totalDue)} due{" "}
                {formatDate(nextInstallment.dueDate)}
              </p>
            </div>
            <Button onClick={() => setPayOpen(true)}>
              <Banknote className="size-4" /> Pay EMI
            </Button>
          </CardBody>
        </Card>
      )}
      {loan.status === "REJECTED" && loan.rejectionReason && (
        <Card className="border-danger-200 bg-danger-50/50">
          <CardBody>
            <p className="text-sm font-medium text-danger-800">Rejection reason</p>
            <p className="mt-1 text-sm text-danger-700">{loan.rejectionReason}</p>
          </CardBody>
        </Card>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <SummaryStat label="Principal" value={formatCurrency(loan.principalAmount)} />
        <SummaryStat label="Interest Rate" value={formatInterestRate(loan.interestRate)} icon={Percent} />
        <SummaryStat label="Tenure" value={`${loan.tenureMonths} months`} icon={CalendarClock} />
        <SummaryStat
          label={loan.status === "ACTIVE" ? "Outstanding" : "EMI Amount"}
          value={formatCurrency(
            loan.status === "ACTIVE" ? loan.outstandingPrincipal : loan.emiAmount,
          )}
        />
      </div>

      {(loan.status === "ACTIVE" || loan.status === "CLOSED") && schedule.length > 0 && (
        <Card>
          <CardBody>
            <RepaymentProgress
              sanctionedAmount={loan.sanctionedAmount}
              outstandingPrincipal={loan.outstandingPrincipal}
              paidInstallments={schedule.filter((e) => e.status === "PAID").length}
              totalInstallments={schedule.length}
            />
          </CardBody>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Loan Details</CardTitle>
        </CardHeader>
        <CardBody className="grid grid-cols-2 gap-x-6 gap-y-4 text-sm sm:grid-cols-3">
          <Detail label="Credit Score" value={loan.creditScore ?? "—"} />
          <Detail label="Risk Category" value={loan.riskCategory ?? "—"} />
          <Detail label="Processing Fee" value={formatCurrency(loan.processingFee)} />
          <Detail label="Total Interest" value={formatCurrency(loan.totalInterestPayable)} />
          <Detail label="Total Payable" value={formatCurrency(loan.totalAmountPayable)} />
          <Detail label="Sanctioned Amount" value={formatCurrency(loan.sanctionedAmount)} />
          <Detail label="Disbursement Date" value={formatDate(loan.disbursementDate)} />
          <Detail label="First EMI Date" value={formatDate(loan.firstEmiDate)} />
          <Detail label="Maturity Date" value={formatDate(loan.maturityDate)} />
          {loan.purpose && <Detail label="Purpose" value={loan.purpose} />}
        </CardBody>
      </Card>

      {schedule.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>EMI Schedule</CardTitle>
          </CardHeader>
          <CardBody>
            <EmiScheduleTable schedule={schedule} />
          </CardBody>
        </Card>
      )}

      {(paymentsQuery.data?.content.length ?? 0) > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Payment History</CardTitle>
          </CardHeader>
          <CardBody className="divide-y divide-ink-100">
            {paymentsQuery.data!.content.map((p) => (
              <div key={p.id} className="flex items-center justify-between py-3 first:pt-0 last:pb-0">
                <div>
                  <p className="font-mono text-xs text-ink-400">{p.paymentReference}</p>
                  <p className="text-sm text-ink-600">
                    {formatDate(p.paymentDate)} · {p.paymentMode ?? p.paymentType}
                  </p>
                </div>
                <p className="font-semibold text-ink-900">{formatCurrency(p.amount)}</p>
              </div>
            ))}
          </CardBody>
        </Card>
      )}

      {nextInstallment && (
        <PayEmiDialog
          open={payOpen}
          onClose={() => setPayOpen(false)}
          loanId={loan.id}
          nextInstallment={nextInstallment}
          onPaid={() => {
            invalidateAll();
            qc.invalidateQueries({ queryKey: ["loans", loanId, "schedule"] });
          }}
        />
      )}
      <ConfirmDialog
        open={approveOpen}
        onClose={() => setApproveOpen(false)}
        onConfirm={() => approveMutation.mutate()}
        loading={approveMutation.isPending}
        title="Approve this loan?"
        description={`This will approve ${loan.loanNumber} for ${formatCurrency(loan.principalAmount)}. The applicant will be notified.`}
        confirmLabel="Approve"
      />
      <ConfirmDialog
        open={disburseOpen}
        onClose={() => setDisburseOpen(false)}
        onConfirm={() => disburseMutation.mutate()}
        loading={disburseMutation.isPending}
        title="Disburse this loan?"
        description="Funds will be marked as disbursed and a full EMI repayment schedule will be generated."
        confirmLabel="Disburse"
      />
      <RejectLoanDialog
        open={rejectOpen}
        onClose={() => setRejectOpen(false)}
        onConfirm={(reason) => rejectMutation.mutate(reason)}
        loading={rejectMutation.isPending}
      />
    </div>
  );
}

function SummaryStat({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value: string;
  icon?: typeof Percent;
}) {
  return (
    <Card>
      <CardBody className="flex items-center gap-3">
        {Icon && (
          <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-50">
            <Icon className="size-4 text-brand-600" />
          </div>
        )}
        <div>
          <p className="text-xs text-ink-500">{label}</p>
          <p className="font-display text-lg font-bold text-ink-900">{value}</p>
        </div>
      </CardBody>
    </Card>
  );
}

function Detail({ label, value }: { label: string; value: string | number }) {
  return (
    <div>
      <dt className="text-xs text-ink-400">{label}</dt>
      <dd className="mt-0.5 font-medium text-ink-800">{value}</dd>
    </div>
  );
}
