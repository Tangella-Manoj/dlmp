import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import toast from "react-hot-toast";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils";
import { paymentsApi } from "@/api/payments";
import { apiErrorMessage } from "@/api/client";
import { paymentSchema, type PaymentFormInput, type PaymentFormValues } from "@/lib/schemas";
import type { EmiScheduleResponse } from "@/types/domain";

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
  const [submitting, setSubmitting] = useState(false);
  const [mode, setMode] = useState<Mode>("EMI");
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
    setValue("paymentType", next);
    setValue("amount", next === "EMI" ? emiCap : 0);
  }

  async function onSubmit(values: PaymentFormValues) {
    if (overCap) {
      toast.error(`This EMI's total due is ₹${emiCap.toFixed(2)} — use "Extra payment" to pay more.`);
      return;
    }
    setSubmitting(true);
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
          ? splitPaymentComponents(nextInstallment, values.amount)
          : { principalAmount: values.amount, interestAmount: 0, penaltyAmount: 0 };
      const payment = await paymentsApi.initiate(
        { ...values, ...split, loanId, remarks: values.remarks || undefined },
        idempotencyKey,
      );
      toast.success(`Payment received — ${payment.paymentReference}`);
      onPaid();
      onClose();
    } catch (err) {
      toast.error(apiErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onClose={onClose} title="Make a Payment">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
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
        <Select label="Payment mode" error={errors.paymentMode?.message} {...register("paymentMode")}>
          <option value="UPI">UPI</option>
          <option value="NEFT">NEFT</option>
          <option value="RTGS">RTGS</option>
          <option value="IMPS">IMPS</option>
        </Select>
        <Input label="Remarks (optional)" placeholder="Note for this payment" {...register("remarks")} />
        <div className="flex gap-3 pt-2">
          <Button type="button" variant="outline" className="flex-1" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" className="flex-1" loading={submitting} disabled={overCap}>
            Pay Now
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
