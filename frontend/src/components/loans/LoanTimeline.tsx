import { Check } from "lucide-react";
import { cn } from "@/lib/utils";
import type { LoanStatus } from "@/types/domain";

const STAGES = ["Applied", "Under Review", "Approved", "Active"] as const;

/** Maps every non-terminal-negative status onto how far along the happy-path
 * lifecycle it is. Terminal negative outcomes (rejected/cancelled/defaulted/NPA)
 * are handled by the existing colored banner in LoanDetail — this timeline
 * only renders for the happy path, where "how far along" is unambiguous. */
function stageIndex(status: LoanStatus): number {
  switch (status) {
    case "DRAFT":
    case "PENDING_REVIEW":
      return 0;
    case "UNDER_REVIEW":
      return 1;
    case "APPROVED":
      return 2;
    case "ACTIVE":
    case "CLOSED":
      return 3;
    default:
      return -1;
  }
}

export function LoanTimeline({ status }: { status: LoanStatus }) {
  const current = stageIndex(status);
  if (current < 0) return null;
  const isClosed = status === "CLOSED";

  return (
    <ol className="flex items-center">
      {STAGES.map((label, i) => {
        const done = i < current;
        const isCurrentStage = i === current;
        return (
          <li key={label} className={cn("flex items-center", i < STAGES.length - 1 && "flex-1")}>
            <div className="flex flex-col items-center gap-1.5">
              <div
                className={cn(
                  "flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold transition-colors",
                  done && "bg-success-600 text-white",
                  isCurrentStage && !isClosed && "bg-brand-600 text-white ring-4 ring-brand-100",
                  isCurrentStage && isClosed && "bg-success-600 text-white",
                  !done && !isCurrentStage && "bg-ink-100 text-ink-400",
                )}
              >
                {done || (isCurrentStage && isClosed) ? <Check className="size-3.5" /> : i + 1}
              </div>
              <span className={cn("hidden text-[11px] font-medium sm:block", i <= current ? "text-ink-700" : "text-ink-400")}>
                {i === 3 && isClosed ? "Closed" : label}
              </span>
            </div>
            {i < STAGES.length - 1 && (
              <div className={cn("mx-2 h-0.5 flex-1 rounded-full transition-colors", i < current ? "bg-success-600" : "bg-ink-100")} />
            )}
          </li>
        );
      })}
    </ol>
  );
}
