import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { ArrowLeft, FileUp, ShieldCheck, TrendingUp } from "lucide-react";
import toast from "react-hot-toast";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { PageSpinner } from "@/components/ui/Spinner";
import { OtpVerifyDialog } from "@/components/consent/OtpVerifyDialog";
import { bankStatementsApi } from "@/api/bankStatements";
import { apiErrorMessage } from "@/api/client";
import { formatCurrency, formatDate } from "@/lib/format";

export function VerifyIncomePage() {
  const qc = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [otpOpen, setOtpOpen] = useState(false);
  const [consented, setConsented] = useState(false);

  const { data: latest, isLoading } = useQuery({
    queryKey: ["bank-statements", "latest"],
    queryFn: bankStatementsApi.latest,
    retry: false,
  });

  async function onFilePicked(file: File) {
    setUploading(true);
    try {
      const result = await bankStatementsApi.analyze(file);
      qc.setQueryData(["bank-statements", "latest"], result);
      if (result.status === "COMPLETED") {
        toast.success("Statement analyzed");
      } else {
        toast.error(result.failureReason ?? "Could not analyze this statement");
      }
    } catch (err) {
      toast.error(apiErrorMessage(err));
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <Link to="/" className="flex items-center gap-1.5 text-sm font-medium text-ink-500 hover:text-ink-700">
        <ArrowLeft className="size-4" /> Back to dashboard
      </Link>

      <Card>
        <CardHeader>
          <CardTitle>Verify your income</CardTitle>
        </CardHeader>
        <CardBody className="space-y-4">
          <p className="text-sm text-ink-500">
            Upload a bank statement (CSV or PDF, max 10MB) and we'll analyze your actual income,
            spending, and account conduct — a real alternative to a credit bureau pull that costs
            nothing. This can unlock a higher loan limit than your income alone would qualify for.
          </p>

          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,.pdf"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) onFilePicked(file);
              e.target.value = "";
            }}
          />
          <Button
            variant="outline"
            className="w-full"
            loading={uploading}
            onClick={() => fileInputRef.current?.click()}
          >
            <FileUp className="size-4" /> Upload bank statement
          </Button>
        </CardBody>
      </Card>

      {isLoading ? (
        <PageSpinner />
      ) : latest && latest.status === "COMPLETED" ? (
        <Card>
          <CardHeader>
            <CardTitle>Latest analysis</CardTitle>
          </CardHeader>
          <CardBody className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <Stat label="Verified monthly income" value={formatCurrency(latest.verifiedMonthlyIncome)} />
              <Stat label="Average monthly balance" value={formatCurrency(latest.avgMonthlyBalance)} />
              <Stat label="Average monthly spending" value={formatCurrency(latest.avgMonthlyOutflow)} />
              <Stat
                label="Bounced payments"
                value={String(latest.bounceCount ?? 0)}
                tone={latest.bounceCount ? "warn" : undefined}
              />
              {latest.reconciliationConfidence !== undefined && (
                <Stat
                  label="Statement verification"
                  value={`${Math.round(latest.reconciliationConfidence * 100)}% confirmed`}
                  tone={latest.reconciliationConfidence < 0.9 ? "warn" : undefined}
                />
              )}
            </div>

            <div className="rounded-xl bg-gradient-to-br from-brand-600 to-brand-800 p-5 text-white">
              <div className="flex items-center gap-2 text-sm font-medium text-brand-100">
                <TrendingUp className="size-4" /> Verified eligible amount
              </div>
              <p className="mt-1 font-display text-2xl font-bold">
                {formatCurrency(latest.verifiedEligibleAmount)}
              </p>
              <p className="mt-2 text-xs text-brand-200">
                Based on {latest.monthsCovered} month(s) of statement data ·{" "}
                {latest.periodStart && latest.periodEnd
                  ? `${formatDate(latest.periodStart)} – ${formatDate(latest.periodEnd)}`
                  : ""}
              </p>
            </div>

            {!consented ? (
              <Button className="w-full" onClick={() => setOtpOpen(true)}>
                <ShieldCheck className="size-4" /> Verify OTP to use this limit
              </Button>
            ) : (
              <Link to="/loans/apply?verifiedLimit=1">
                <Button className="w-full">Apply using this verified limit</Button>
              </Link>
            )}
          </CardBody>
        </Card>
      ) : latest && latest.status === "FAILED" ? (
        <Card>
          <CardBody>
            <p className="text-sm text-danger-600">{latest.failureReason}</p>
          </CardBody>
        </Card>
      ) : null}

      <OtpVerifyDialog
        open={otpOpen}
        onClose={() => setOtpOpen(false)}
        purpose="LIMIT_INCREASE"
        onVerified={() => setConsented(true)}
      />
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: "warn" }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-ink-400">{label}</p>
      <p className={tone === "warn" ? "text-lg font-semibold text-warning-600" : "text-lg font-semibold text-ink-900"}>
        {value}
      </p>
    </div>
  );
}
