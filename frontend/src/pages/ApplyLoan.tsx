import { useMemo, useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { ArrowLeft, Sparkles } from "lucide-react";
import { Link } from "react-router-dom";
import { loansApi } from "@/api/loans";
import { apiErrorMessage } from "@/api/client";
import { Input, Select, Textarea } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/Card";
import {
  loanApplicationSchema,
  type LoanApplicationFormInput,
  type LoanApplicationFormValues,
} from "@/lib/schemas";
import { LOAN_TYPE_BOUNDS, LOAN_TYPE_LABELS } from "@/lib/loanMeta";
import { estimateEmi, estimateTotalInterest } from "@/lib/emiCalculator";
import { formatCurrency } from "@/lib/format";
import type { LoanType } from "@/types/domain";

export function ApplyLoanPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    control,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<LoanApplicationFormInput, unknown, LoanApplicationFormValues>({
    resolver: zodResolver(loanApplicationSchema),
    defaultValues: { loanType: "PERSONAL", tenureMonths: 12 },
  });

  const loanType = watch("loanType") as LoanType;
  const principal = Number(watch("principalAmount")) || 0;
  const tenure = Number(watch("tenureMonths")) || 0;
  const bounds = LOAN_TYPE_BOUNDS[loanType];

  const preview = useMemo(() => {
    const emi = estimateEmi(principal, bounds.rate, tenure);
    const interest = estimateTotalInterest(emi, tenure, principal);
    return { emi, interest, total: principal + Math.max(interest, 0) };
  }, [principal, tenure, bounds.rate]);

  async function onSubmit(values: LoanApplicationFormValues) {
    setSubmitting(true);
    try {
      const loan = await loansApi.apply({
        ...values,
        purpose: values.purpose || undefined,
        existingDebts: values.existingDebts || undefined,
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

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Link to="/" className="flex items-center gap-1.5 text-sm font-medium text-ink-500 hover:text-ink-700">
        <ArrowLeft className="size-4" /> Back to dashboard
      </Link>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle>Apply for a Loan</CardTitle>
          </CardHeader>
          <CardBody>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              <Controller
                control={control}
                name="loanType"
                render={({ field }) => (
                  <Select label="Loan type" error={errors.loanType?.message} {...field}>
                    {Object.entries(LOAN_TYPE_LABELS).map(([value, label]) => (
                      <option key={value} value={value}>
                        {label} ({LOAN_TYPE_BOUNDS[value as LoanType].rate}% p.a.)
                      </option>
                    ))}
                  </Select>
                )}
              />

              <div className="grid grid-cols-2 gap-3">
                <Input
                  label="Loan amount (₹)"
                  type="number"
                  placeholder={String(bounds.min)}
                  hint={`₹${bounds.min.toLocaleString("en-IN")} – ₹${bounds.max.toLocaleString("en-IN")}`}
                  error={errors.principalAmount?.message}
                  {...register("principalAmount")}
                />
                <Input
                  label="Tenure (months)"
                  type="number"
                  placeholder="12"
                  hint={`Max ${bounds.maxTenure} months`}
                  error={errors.tenureMonths?.message}
                  {...register("tenureMonths")}
                />
              </div>

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

              <Button type="submit" size="lg" className="w-full" loading={submitting}>
                Submit Application
              </Button>
            </form>
          </CardBody>
        </Card>

        <div className="lg:col-span-2">
          <Card className="sticky top-24 bg-gradient-to-br from-brand-600 to-brand-800 text-white">
            <CardBody>
              <div className="mb-4 flex items-center gap-2">
                <Sparkles className="size-4 text-brand-200" />
                <p className="text-sm font-semibold text-brand-100">Estimated EMI</p>
              </div>
              <p className="font-display text-3xl font-bold">
                {preview.emi > 0 ? formatCurrency(preview.emi) : "—"}
                <span className="text-base font-normal text-brand-200">/month</span>
              </p>

              <dl className="mt-6 space-y-3 border-t border-white/20 pt-4 text-sm">
                <Row label="Interest rate" value={`${bounds.rate}% p.a.`} />
                <Row label="Tenure" value={tenure ? `${tenure} months` : "—"} />
                <Row
                  label="Total interest"
                  value={preview.interest > 0 ? formatCurrency(preview.interest) : "—"}
                />
                <Row
                  label="Total payable"
                  value={preview.total > principal ? formatCurrency(preview.total) : "—"}
                />
              </dl>
              <p className="mt-6 text-xs text-brand-200">
                This is an estimate. Your final EMI and credit decision are calculated after
                submission based on a full risk assessment.
              </p>
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <dt className="text-brand-200">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}
