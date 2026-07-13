import type { HTMLAttributes } from "react";
import { cn } from "@/lib/utils";
import { LOAN_STATUS_META, STATUS_TONE_CLASSES } from "@/lib/loanMeta";
import type { LoanStatus } from "@/types/domain";

export function Badge({ className, ...props }: HTMLAttributes<HTMLSpanElement>) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset",
        className,
      )}
      {...props}
    />
  );
}

export function LoanStatusBadge({ status }: { status: LoanStatus }) {
  const meta = LOAN_STATUS_META[status];
  return <Badge className={STATUS_TONE_CLASSES[meta.tone]}>{meta.label}</Badge>;
}
