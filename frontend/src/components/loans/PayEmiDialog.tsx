import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import toast from "react-hot-toast";
import { ArrowLeft, Banknote, Building2, CheckCircle2, Loader2, Smartphone, Zap } from "lucide-react";
import { Dialog } from "@/components/ui/Dialog";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils";
import { paymentsApi } from "@/api/payments";
import { apiErrorMessage } from "@/api/client";
import { paymentSchema, type PaymentFormInput, type PaymentFormValues } from "@/lib/schemas";
import { formatCurrency } from "@/lib/format";
import type { EmiScheduleResponse, PaymentResponse } from "@/types/domain";

/**
 * Splits a payment amount into penalty/interest/principal using the same
 * penalty -> interest -> principal waterfall as loan-service's
 * LoanRepaymentService, so payment-service's ledger records an accurate
 * split instead of defaulting the whole amount to "principal".
 *
 * Only valid for amount <= installment.totalDue — the dialog enforces that
 * cap in EMI mode. An amount spanning into a later installment would need
 * the caller to know that installment's own components too (which this
 * dialog doesn't fetch), so overpayment goes through "extra payment" mode
 * instead, where the full amount is booked as principal.
 */
function splitPaymentComponents(installment: EmiScheduleResponse, amount: number) {
  const round2 = (n: number) => Math.round(n * 100) / 100;
  const bucket = (paid: number) => {
    const afterPenalty = Math.max(paid - installment.penaltyAmount, 0);
    return {
      penaltyPaid: Math.min(paid, installment.penaltyAmount),
      interestPaid: Math.min(afterPenalty, installment.interestComponent),
    };
  };
  const before = bucket(installment.paidAmount);
  const after = bucket(installment.paidAmount + amount);
  const penaltyAmount = round2(after.penaltyPaid - before.penaltyPaid);
  const interestAmount = round2(after.interestPaid - before.interestPaid);
  return { penaltyAmount, interestAmount, principalAmount: round2(amount - penaltyAmount - interestAmount) };
}

type Mode = "EMI" | "PREPAYMENT";
type Step = "amount" | "method" | "processing" | "success";

const UPI_APPS = [
  { id: "gpay", name: "Google Pay", tint: "bg-blue-50 text-blue-700 ring-blue-100" },
  { id: "phonepe", name: "PhonePe", tint: "bg-violet-50 text-violet-700 ring-violet-100" },
  { id: "paytm", name: "Paytm", tint: "bg-sky-50 text-sky-700 ring-sky-100" },
  { id: "bhim", name: "BHIM UPI", tint: "bg-orange-50 text-orange-700 ring-orange-100" },
];

const BANKS = [
  "State Bank of India",
  "HDFC Bank",
  "ICICI Bank",
  "Axis Bank",
  "Kotak Mahindra Bank",
  "Punjab National Bank",
];

const RTGS_THRESHOLD = 200_000;

export function PayEmiDialog({
  open,
  onClose,
  loanId,
  nextInstallment,
  onPaid,
}: {
  open: boolean;
  onClose: () => void;
  loanId: string;
  nextInstallment?: EmiScheduleResponse;
  onPaid: () => void;
}) {
  const [step, setStep] = useState<Step>("amount");
  const [mode, setMode] = useState<Mode>("EMI");
  const [methodLabel, setMethodLabel] = useState("");
  const [result, setResult] = useState<PaymentResponse | null>(null);
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<PaymentFormInput, unknown, PaymentFormValues>({
    resolver: zodResolver(paymentSchema),
    values: {
      amount: mode === "EMI" ? (nextInstallment?.totalDue ?? 0) : 0,
      paymentType: mode,
      paymentMode: "UPI",
      remarks: "",
    },
  });

  const amount = Number(watch("amount")) || 0;
  const emiCap = nextInstallment?.totalDue ?? 0;
  const overCap = mode === "EMI" && amount > emiCap;

  function selectMode(next: Mode) {
    setMode(next);
    setValue("amount", next === "EMI" ? emiCap : 0);
  }

  function reset() {
    setStep("amount");
    setMethodLabel("");
    setResult(null);
  }

  function handleClose() {
    if (step === "processing") return; // don't let a payment in flight get abandoned mid-request
    reset();
    onClose();
  }

  async function chooseMethod(backendMode: PaymentFormValues["paymentMode"], label: string) {
    setValue("paymentMode", backendMode);
    setMethodLabel(label);
    setStep("processing");

    const values = watch();
    const paidAmount = Number(values.amount) || 0;
    try {
      const idempotencyKey =
        typeof crypto !== "undefined" && "randomUUID" in crypto
          ? crypto.randomUUID()
          : `${loanId}-${Date.now()}`;
      // EMI mode: split against the known installment components. Prepayment
      // mode: a voluntary lump sum isn't tied to one installment's schedule,
      // so it's booked entirely as principal — loan-service's own waterfall
      // still recomputes the true split against whichever installments it
      // actually pays down, keeping the loan balance correct either way.
      const split =
        mode === "EMI" && nextInstallment
          ? splitPaymentComponents(nextInstallment, paidAmount)
          : { principalAmount: paidAmount, interestAmount: 0, penaltyAmount: 0 };

      // A brief simulated processing delay — same shape as a real UPI
      // intent hand-off / netbanking redirect round-trip — so the method
      // step doesn't jump straight to "done" and feel like nothing happened.
      const [payment] = await Promise.all([
        paymentsApi.initiate(
          {
            loanId,
            amount: paidAmount,
            paymentType: mode,
            paymentMode: backendMode,
            remarks: values.remarks || undefined,
            ...split,
          },
          idempotencyKey,
        ),
        new Promise((r) => setTimeout(r, 1400)),
      ]);
      setResult(payment);
      setStep("success");
      onPaid();
    } catch (err) {
      toast.error(apiErrorMessage(err));
      setStep("method");
    }
  }

  function onAmountSubmit() {
    if (overCap) {
      toast.error(`This EMI's total due is ₹${emiCap.toFixed(2)} — use "Extra payment" to pay more.`);
      return;
    }
    setStep("method");
  }

  const title =
    step === "amount" ? "Make a Payment" : step === "method" ? "Choose payment method" : step === "processing" ? "Processing payment" : "Payment successful";

  return (
    <Dialog open={open} onClose={handleClose} title={title}>
      {step === "amount" && (
        <form onSubmit={handleSubmit(onAmountSubmit)} className="space-y-4" noValidate>
          <div className="grid grid-cols-2 gap-2 rounded-lg bg-ink-100 p-1">
            <button
              type="button"
              onClick={() => selectMode("EMI")}
              className={cn(
                "rounded-md py-1.5 text-sm font-medium transition-colors",
                mode === "EMI" ? "bg-white text-ink-900 shadow-sm" : "text-ink-500 hover:text-ink-700",
              )}
            >
              Pay this EMI
            </button>
            <button
              type="button"
              onClick={() => selectMode("PREPAYMENT")}
              className={cn(
                "rounded-md py-1.5 text-sm font-medium transition-colors",
                mode === "PREPAYMENT" ? "bg-white text-ink-900 shadow-sm" : "text-ink-500 hover:text-ink-700",
              )}
            >
              Extra payment
            </button>
          </div>

          {mode === "EMI" && nextInstallment && (
            <p className="rounded-lg bg-brand-50 px-3 py-2 text-sm text-brand-700">
              Installment #{nextInstallment.installmentNumber} — due{" "}
              {new Date(nextInstallment.dueDate).toLocaleDateString("en-IN")}, total due ₹{emiCap.toFixed(2)}
            </p>
          )}
          {mode === "PREPAYMENT" && (
            <p className="rounded-lg bg-brand-50 px-3 py-2 text-sm text-brand-700">
              Pays down your principal ahead of schedule, applied to future installments oldest-first.
            </p>
          )}

          <Input
            label="Amount (₹)"
            type="number"
            step="0.01"
            max={mode === "EMI" ? emiCap : undefined}
            error={overCap ? `Cannot exceed this EMI's total due (₹${emiCap.toFixed(2)})` : errors.amount?.message}
            {...register("amount")}
          />
          <Input label="Remarks (optional)" placeholder="Note for this payment" {...register("remarks")} />
          <div className="flex gap-3 pt-2">
            <Button type="button" variant="outline" className="flex-1" onClick={handleClose}>
              Cancel
            </Button>
            <Button type="submit" className="flex-1" disabled={overCap || amount <= 0}>
              Continue
            </Button>
          </div>
        </form>
      )}

      {step === "method" && (
        <div className="space-y-5">
          <div className="flex items-center justify-between rounded-lg bg-ink-50 px-3 py-2">
            <span className="text-sm text-ink-500">Amount to pay</span>
            <span className="font-display text-lg font-bold text-ink-900">{formatCurrency(amount)}</span>
          </div>

          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-400">UPI</p>
            <div className="grid grid-cols-2 gap-2">
              {UPI_APPS.map((app) => (
                <button
                  key={app.id}
                  type="button"
                  onClick={() => chooseMethod("UPI", app.name)}
                  className={cn(
                    "flex items-center gap-2 rounded-xl px-3 py-3 text-sm font-medium ring-1 transition-transform hover:scale-[1.02]",
                    app.tint,
                  )}
                >
                  <Smartphone className="size-4 shrink-0" />
                  {app.name}
                </button>
              ))}
            </div>
          </div>

          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-400">Net Banking</p>
            <div className="space-y-1.5">
              {BANKS.slice(0, 3).map((bank) => (
                <button
                  key={bank}
                  type="button"
                  onClick={() => chooseMethod(amount >= RTGS_THRESHOLD ? "RTGS" : "NEFT", bank)}
                  className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm font-medium text-ink-700 ring-1 ring-ink-200 transition-colors hover:bg-ink-50"
                >
                  <Building2 className="size-4 shrink-0 text-ink-400" />
                  {bank}
                </button>
              ))}
            </div>
          </div>

          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-400">Instant Transfer</p>
            <button
              type="button"
              onClick={() => chooseMethod("IMPS", "IMPS")}
              className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm font-medium text-ink-700 ring-1 ring-ink-200 transition-colors hover:bg-ink-50"
            >
              <Zap className="size-4 shrink-0 text-ink-400" />
              IMPS — instant bank transfer
            </button>
          </div>

          <Button type="button" variant="ghost" className="w-full" onClick={() => setStep("amount")}>
            <ArrowLeft className="size-4" /> Back
          </Button>
        </div>
      )}

      {step === "processing" && (
        <div className="flex flex-col items-center gap-4 py-10 text-center">
          <div className="flex size-16 items-center justify-center rounded-full bg-brand-50">
            <Loader2 className="size-8 animate-spin text-brand-600" />
          </div>
          <div>
            <p className="font-semibold text-ink-900">Processing via {methodLabel}…</p>
            <p className="mt-1 text-sm text-ink-500">Don't close this window.</p>
          </div>
        </div>
      )}

      {step === "success" && result && (
        <div className="flex flex-col items-center gap-4 py-6 text-center">
          <div className="flex size-16 items-center justify-center rounded-full bg-success-50 animate-fade-in">
            <CheckCircle2 className="size-9 text-success-600" />
          </div>
          <div>
            <p className="font-display text-2xl font-bold text-ink-900">{formatCurrency(result.amount)}</p>
            <p className="mt-1 text-sm text-ink-500">Paid via {methodLabel}</p>
          </div>
          <div className="flex w-full items-center justify-between rounded-lg bg-ink-50 px-3 py-2 text-sm">
            <span className="flex items-center gap-1.5 text-ink-500">
              <Banknote className="size-3.5" /> Reference
            </span>
            <span className="font-mono text-ink-700">{result.paymentReference}</span>
          </div>
          <Button className="w-full" onClick={handleClose}>
            Done
          </Button>
        </div>
      )}
    </Dialog>
  );
}
