import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import toast from "react-hot-toast";
import { Dialog } from "@/components/ui/Dialog";
import { Input, Select } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { paymentsApi } from "@/api/payments";
import { apiErrorMessage } from "@/api/client";
import { paymentSchema, type PaymentFormInput, type PaymentFormValues } from "@/lib/schemas";
import type { EmiScheduleResponse } from "@/types/domain";

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
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PaymentFormInput, unknown, PaymentFormValues>({
    resolver: zodResolver(paymentSchema),
    values: {
      amount: nextInstallment?.totalDue ?? 0,
      paymentType: "EMI",
      paymentMode: "UPI",
      remarks: "",
    },
  });

  async function onSubmit(values: PaymentFormValues) {
    setSubmitting(true);
    try {
      const idempotencyKey =
        typeof crypto !== "undefined" && "randomUUID" in crypto
          ? crypto.randomUUID()
          : `${loanId}-${Date.now()}`;
      const payment = await paymentsApi.initiate(
        { ...values, loanId, remarks: values.remarks || undefined },
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
    <Dialog open={open} onClose={onClose} title="Pay EMI">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {nextInstallment && (
          <p className="rounded-lg bg-brand-50 px-3 py-2 text-sm text-brand-700">
            Installment #{nextInstallment.installmentNumber} — due{" "}
            {new Date(nextInstallment.dueDate).toLocaleDateString("en-IN")}
          </p>
        )}
        <Input
          label="Amount (₹)"
          type="number"
          step="0.01"
          error={errors.amount?.message}
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
          <Button type="submit" className="flex-1" loading={submitting}>
            Pay Now
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
