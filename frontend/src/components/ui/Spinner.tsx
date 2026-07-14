import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

export function Spinner({ className }: { className?: string }) {
  return (
    <span role="status" aria-live="polite">
      <Loader2 className={cn("size-5 animate-spin text-brand-600", className)} />
      <span className="sr-only">Loading…</span>
    </span>
  );
}

export function PageSpinner() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <Spinner className="size-8" />
    </div>
  );
}
