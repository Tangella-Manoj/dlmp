import { useEffect, useMemo, useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { ArrowLeft, ArrowRight, CheckCircle2, ShieldCheck, Sparkles } from "lucide-react";
import { Link } from "react-router-dom";
import { loansApi } from "@/api/loans";
import { bankStatementsApi } from "@/api/bankStatements";
import { apiErrorMessage } from "@/api/client";
import { Input, Textarea } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/Card";
import { Stepper } from "@/components/ui/Stepper";
import { Slider } from "@/components/ui/Slider";
import { OtpVerifyDialog } from "@/components/consent/OtpVerifyDialog";
import { LoanTypePicker } from "@/components/loans/LoanTypePicker";
import {
  buildLoanApplicationSchema,
  type LoanApplicationFormInput,
  type LoanApplicationFormValues,
} from "@/lib/schemas";
import { LOAN_TYPE_BOUNDS, LOAN_TYPE_LABELS } from "@/lib/loanMeta";
import { estimateEmi, estimateTotalInterest } from "@/lib/emiCalculator";
import { formatCurrency } from "@/lib/format";
import type { LoanType } from "@/types/domain";

const STEPS = ["Loan Details", "Your Finances", "Review & Confirm"];
const STEP_FIELDS: (keyof LoanApplicationFormInput)[][] = [
  ["loanType", "principalAmount", "tenureMonths"],
  ["monthlyIncome", "existingDebts", "purpose"],
  [],
];

export function ApplyLoanPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [searchParams] = useSearchParams();
  const useVerifiedLimit = searchParams.get("verifiedLimit") === "1";

  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [appConsented, setAppConsented] = useState(false);
  const [otpOpen, setOtpOpen] = useState(false);
  const [pendingValues, setPendingValues] = useState<LoanApplicationFormValues | null>(null);

  const { data: verifiedAnalysis } = useQuery({
    queryKey: ["bank-statements", "latest"],
    queryFn: bankStatementsApi.latest,
    enabled: useVerifiedLimit,
    retry: false,
  });
  const verifiedCap = useVerifiedLimit ? verifiedAnalysis?.verifiedEligibleAmount : undefined;

  const schema = useMemo(() => buildLoanApplicationSchema(verifiedCap), [verifiedCap]);
  const {
    register,
    control,
    handleSubmit,
    watch,
    trigger,
    formState: { errors },
  } = useForm<LoanApplicationFormInput, unknown, LoanApplicationFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { loanType: "PERSONAL", tenureMonths: 12 },
    mode: "onChange",
  });

  const loanType = watch("loanType") as LoanType;
  const principal = Number(watch("principalAmount")) || 0;
  const tenure = Number(watch("tenureMonths")) || 0;
  const bounds = LOAN_TYPE_BOUNDS[loanType];
  const displayMax = verifiedCap ? Math.max(bounds.max, verifiedCap) : bounds.max;
  const amountStep = Math.max(1000, Math.round((displayMax - bounds.min) / 200 / 1000) * 1000);

  const preview = useMemo(() => {
    const emi = estimateEmi(principal, bounds.rate, tenure);
    const interest = estimateTotalInterest(emi, tenure, principal);
    return { emi, interest, total: principal + Math.max(interest, 0) };
  }, [principal, tenure, bounds.rate]);

  async function submitApplication(values: LoanApplicationFormValues) {
    setSubmitting(true);
    try {
      const loan = await loansApi.apply({
        ...values,
        purpose: values.purpose || undefined,
        existingDebts: values.existingDebts || undefined,
        useVerifiedLimit,
      });
      toast.success(`Application submitted — ${loan.loanNumber}`);
      await qc.invalidateQueries({ queryKey: ["loans"] });
      navigate(`/loans/${loan.id}`);
    } catch (err) {
      toast.error(apiErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  function onSubmit(values: LoanApplicationFormValues) {
    if (!appConsented) {
      setPendingValues(values);
      setOtpOpen(true);
      return;
    }
    submitApplication(values);
  }

  // Once the app-application OTP is verified while a submission was waiting on it, proceed automatically.
  useEffect(() => {
    if (appConsented && pendingValues) {
      const values = pendingValues;
      setPendingValues(null);
      submitApplication(values);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [appConsented]);

  async function goNext() {
    const fields = STEP_FIELDS[step];
    const valid = fields.length === 0 || (await trigger(fields));
    if (valid) setStep((s) => Math.min(s + 1, STEPS.length - 1));
  }

  function goBack() {
    setStep((s) => Math.max(s - 1, 0));
  }

  const values = watch();

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Link to="/" className="flex items-center gap-1.5 text-sm font-medium text-ink-500 hover:text-ink-700">
        <ArrowLeft className="size-4" /> Back to dashboard
      </Link>

      {useVerifiedLimit ? (
        <div className="flex items-center gap-2 rounded-xl bg-brand-50 px-4 py-3 text-sm text-brand-700">
          <ShieldCheck className="size-4 shrink-0" />
          {verifiedCap
            ? `Using your verified limit — eligible up to ${formatCurrency(verifiedCap)}.`
            : "Loading your verified limit…"}
        </div>
      ) : (
        <p className="text-sm text-ink-500">
          Need a higher amount than your income alone qualifies for?{" "}
          <Link to="/verify-income" className="font-medium text-brand-600 hover:underline">
            Verify your income with a bank statement
          </Link>
          .
        </p>
      )}

      <Card>
        <CardHeader>
          <Stepper steps={STEPS} currentStep={step} />
        </CardHeader>

        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <CardBody className="space-y-6">
            {step === 0 && (
              <div className="space-y-6">
                <Controller
                  control={control}
                  name="loanType"
                  render={({ field }) => <LoanTypePicker value={field.value as LoanType} onChange={field.onChange} />}
                />

                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <label className="text-sm font-medium text-ink-700">Loan amount</label>
                    <span className="font-display text-lg font-bold text-brand-700">
                      {formatCurrency(principal || bounds.min)}
                    </span>
                  </div>
                  <Controller
                    control={control}
                    name="principalAmount"
                    render={({ field }) => (
                      <Slider
                        min={bounds.min}
                        max={displayMax}
                        step={amountStep}
                        value={Number(field.value) || bounds.min}
                        onValueChange={field.onChange}
                      />
                    )}
                  />
                  <div className="mt-1 flex justify-between text-xs text-ink-400">
                    <span>₹{bounds.min.toLocaleString("en-IN")}</span>
                    <span>₹{displayMax.toLocaleString("en-IN")}</span>
                  </div>
                  <Input
                    className="mt-3"
                    type="number"
                    error={errors.principalAmount?.message}
                    {...register("principalAmount")}
                  />
                </div>

                <div>
                  <div className="mb-1.5 flex items-baseline justify-between">
                    <label className="text-sm font-medium text-ink-700">Tenure</label>
                    <span className="font-display text-lg font-bold text-brand-700">
                      {tenure || 1} month{tenure === 1 ? "" : "s"}
                    </span>
                  </div>
                  <Controller
                    control={control}
                    name="tenureMonths"
                    render={({ field }) => (
                      <Slider
                        min={1}
                        max={bounds.maxTenure}
                        step={1}
                        value={Number(field.value) || 1}
                        onValueChange={field.onChange}
                      />
                    )}
                  />
                  <div className="mt-1 flex justify-between text-xs text-ink-400">
                    <span>1 month</span>
                    <span>{bounds.maxTenure} months</span>
                  </div>
                  <Input
                    className="mt-3"
                    type="number"
                    error={errors.tenureMonths?.message}
                    {...register("tenureMonths")}
                  />
                </div>
              </div>
            )}

            {step === 1 && (
              <div className="space-y-4">
                <Input
                  label="Monthly income (₹)"
                  type="number"
                  placeholder="75000"
                  error={errors.monthlyIncome?.message}
                  {...register("monthlyIncome")}
                />
                <Input
                  label="Existing monthly debts (₹, optional)"
                  type="number"
                  placeholder="0"
                  error={errors.existingDebts?.message}
                  {...register("existingDebts")}
                />
                <Textarea
                  label="Purpose (optional)"
                  placeholder="e.g. Home renovation"
                  error={errors.purpose?.message}
                  {...register("purpose")}
                />
              </div>
            )}

            {step === 2 && (
              <div className="space-y-4">
                <div className="rounded-xl bg-ink-50 p-4">
                  <p className="mb-3 text-sm font-semibold text-ink-700">Review your application</p>
                  <dl className="grid grid-cols-2 gap-y-3 text-sm">
                    <ReviewRow label="Loan type" value={LOAN_TYPE_LABELS[loanType]} />
                    <ReviewRow label="Amount" value={formatCurrency(principal)} />
                    <ReviewRow label="Tenure" value={`${tenure} months`} />
                    <ReviewRow label="Interest rate" value={`${bounds.rate}% p.a.`} />
                    <ReviewRow label="Monthly income" value={formatCurrency(Number(values.monthlyIncome) || 0)} />
                    <ReviewRow
                      label="Existing debts"
                      value={formatCurrency(Number(values.existingDebts) || 0)}
                    />
                    {values.purpose && (
                      <div className="col-span-2">
                        <dt className="text-xs text-ink-400">Purpose</dt>
                        <dd className="mt-0.5 text-ink-800">{values.purpose}</dd>
                      </div>
                    )}
                  </dl>
                </div>
                <div className="flex items-start gap-2 rounded-xl border border-brand-100 bg-brand-50/60 p-4 text-sm text-brand-800">
                  <ShieldCheck className="mt-0.5 size-4 shrink-0" />
                  <p>
                    We'll email a one-time code to confirm it's really you before this application is
                    submitted — required for every loan application, no matter the amount.
                  </p>
                </div>
              </div>
            )}
          </CardBody>

          <div className="flex items-center justify-between border-t border-ink-100 px-6 py-4">
            {step > 0 ? (
              <Button type="button" variant="outline" onClick={goBack}>
                <ArrowLeft className="size-4" /> Back
              </Button>
            ) : (
              <span />
            )}

            {step < STEPS.length - 1 ? (
              <Button key="next" type="button" onClick={goNext}>
                Next <ArrowRight className="size-4" />
              </Button>
            ) : (
              <Button key="submit" type="submit" loading={submitting}>
                <CheckCircle2 className="size-4" />
                {appConsented ? "Submit Application" : "Verify & Submit Application"}
              </Button>
            )}
          </div>
        </form>
      </Card>

      <Card className="bg-gradient-to-br from-brand-600 to-brand-800 text-white">
        <CardBody>
          <div className="mb-4 flex items-center gap-2">
            <Sparkles className="size-4 text-brand-200" />
            <p className="text-sm font-semibold text-brand-100">Estimated EMI</p>
          </div>
          <p className="font-display text-3xl font-bold">
            {preview.emi > 0 ? formatCurrency(preview.emi) : "—"}
            <span className="text-base font-normal text-brand-200">/month</span>
          </p>

          <dl className="mt-6 grid grid-cols-3 gap-4 border-t border-white/20 pt-4 text-sm">
            <MiniStat label="Interest rate" value={`${bounds.rate}%`} />
            <MiniStat label="Total interest" value={preview.interest > 0 ? formatCurrency(preview.interest) : "—"} />
            <MiniStat label="Total payable" value={preview.total > principal ? formatCurrency(preview.total) : "—"} />
          </dl>
          <p className="mt-6 text-xs text-brand-200">
            This is an estimate. Your final EMI and credit decision are calculated after submission
            based on a full risk assessment.
          </p>
        </CardBody>
      </Card>

      <OtpVerifyDialog
        open={otpOpen}
        onClose={() => setOtpOpen(false)}
        purpose="LOAN_APPLICATION"
        onVerified={() => setAppConsented(true)}
      />
    </div>
  );
}

function ReviewRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-ink-400">{label}</dt>
      <dd className="mt-0.5 font-medium text-ink-800">{value}</dd>
    </div>
  );
}

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-brand-200">{label}</dt>
      <dd className="mt-0.5 font-semibold">{value}</dd>
    </div>
  );
}
