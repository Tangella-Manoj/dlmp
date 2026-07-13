import { Badge } from "@/components/ui/Badge";
import { formatCurrency, formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { EmiScheduleResponse } from "@/types/domain";

const statusClasses: Record<EmiScheduleResponse["status"], string> = {
  PAID: "bg-success-50 text-success-700 ring-success-500/20",
  PARTIAL: "bg-warning-50 text-warning-700 ring-warning-500/20",
  PENDING: "bg-ink-100 text-ink-600 ring-ink-200",
};

export function EmiScheduleTable({ schedule }: { schedule: EmiScheduleResponse[] }) {
  return (
    <div className="overflow-x-auto scrollbar-thin">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-ink-200 text-xs uppercase tracking-wide text-ink-400">
            <th className="py-2.5 pr-4 font-medium">#</th>
            <th className="py-2.5 pr-4 font-medium">Due Date</th>
            <th className="py-2.5 pr-4 font-medium">EMI</th>
            <th className="py-2.5 pr-4 font-medium">Principal</th>
            <th className="py-2.5 pr-4 font-medium">Interest</th>
            <th className="py-2.5 pr-4 font-medium">Closing Balance</th>
            <th className="py-2.5 pr-4 font-medium">Status</th>
          </tr>
        </thead>
        <tbody>
          {schedule.map((row) => (
            <tr
              key={row.id}
              className={cn(
                "border-b border-ink-100 last:border-0",
                row.daysOverdue > 0 && row.status !== "PAID" && "bg-danger-50/40",
              )}
            >
              <td className="py-2.5 pr-4 text-ink-500">{row.installmentNumber}</td>
              <td className="py-2.5 pr-4 text-ink-700">
                {formatDate(row.dueDate)}
                {row.daysOverdue > 0 && row.status !== "PAID" && (
                  <span className="ml-1.5 text-xs font-medium text-danger-600">
                    {row.daysOverdue}d overdue
                  </span>
                )}
              </td>
              <td className="py-2.5 pr-4 font-medium text-ink-900">{formatCurrency(row.emiAmount)}</td>
              <td className="py-2.5 pr-4 text-ink-600">{formatCurrency(row.principalComponent)}</td>
              <td className="py-2.5 pr-4 text-ink-600">{formatCurrency(row.interestComponent)}</td>
              <td className="py-2.5 pr-4 text-ink-600">{formatCurrency(row.closingBalance)}</td>
              <td className="py-2.5 pr-4">
                <Badge className={statusClasses[row.status]}>{row.status}</Badge>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
