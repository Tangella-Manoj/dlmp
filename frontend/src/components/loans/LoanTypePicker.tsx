import { Briefcase, Car, GraduationCap, Home, Sparkles, Wallet } from "lucide-react";
import { cn } from "@/lib/utils";
import { LOAN_TYPE_BOUNDS, LOAN_TYPE_LABELS } from "@/lib/loanMeta";
import type { LoanType } from "@/types/domain";

const ICONS: Record<LoanType, typeof Home> = {
  PERSONAL: Wallet,
  HOME: Home,
  VEHICLE: Car,
  BUSINESS: Briefcase,
  EDUCATION: GraduationCap,
  GOLD: Sparkles,
};

interface LoanTypePickerProps {
  value: LoanType;
  onChange: (value: LoanType) => void;
}

export function LoanTypePicker({ value, onChange }: LoanTypePickerProps) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {(Object.keys(LOAN_TYPE_LABELS) as LoanType[]).map((type) => {
        const Icon = ICONS[type];
        const selected = value === type;
        return (
          <button
            key={type}
            type="button"
            onClick={() => onChange(type)}
            className={cn(
              "flex flex-col items-start gap-2 rounded-xl border p-3.5 text-left transition-all",
              selected
                ? "border-brand-600 bg-brand-50 ring-2 ring-brand-600"
                : "border-ink-200 bg-white hover:border-brand-300 hover:bg-brand-50/40",
            )}
          >
            <div
              className={cn(
                "flex size-9 items-center justify-center rounded-lg",
                selected ? "bg-brand-600 text-white" : "bg-ink-100 text-ink-500",
              )}
            >
              <Icon className="size-4" />
            </div>
            <div>
              <p className={cn("text-sm font-semibold", selected ? "text-brand-800" : "text-ink-800")}>
                {LOAN_TYPE_LABELS[type]}
              </p>
              <p className="text-xs text-ink-400">{LOAN_TYPE_BOUNDS[type].rate}% p.a.</p>
            </div>
          </button>
        );
      })}
    </div>
  );
}
