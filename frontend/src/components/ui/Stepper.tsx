import { Check } from "lucide-react";
import { cn } from "@/lib/utils";

interface StepperProps {
  steps: string[];
  currentStep: number;
}

export function Stepper({ steps, currentStep }: StepperProps) {
  return (
    <ol className="flex items-center">
      {steps.map((label, i) => {
        const isDone = i < currentStep;
        const isCurrent = i === currentStep;
        return (
          <li key={label} className={cn("flex items-center", i < steps.length - 1 && "flex-1")}>
            <div className="flex flex-col items-center gap-1.5">
              <div
                className={cn(
                  "flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold transition-colors",
                  isDone && "bg-brand-600 text-white",
                  isCurrent && "bg-brand-600 text-white ring-4 ring-brand-100",
                  !isDone && !isCurrent && "bg-ink-100 text-ink-400",
                )}
              >
                {isDone ? <Check className="size-4" /> : i + 1}
              </div>
              <span
                className={cn(
                  "hidden text-xs font-medium sm:block",
                  (isDone || isCurrent) ? "text-ink-700" : "text-ink-400",
                )}
              >
                {label}
              </span>
            </div>
            {i < steps.length - 1 && (
              <div className={cn("mx-2 h-0.5 flex-1 rounded-full transition-colors", isDone ? "bg-brand-600" : "bg-ink-100")} />
            )}
          </li>
        );
      })}
    </ol>
  );
}
