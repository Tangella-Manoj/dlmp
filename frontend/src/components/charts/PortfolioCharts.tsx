import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/Card";
import { formatCurrency } from "@/lib/format";
import { LOAN_STATUS_META } from "@/lib/loanMeta";

const TONE_HEX: Record<string, string> = {
  slate: "#94a3b8",
  amber: "#f59e0b",
  blue: "#3b82f6",
  green: "#22c55e",
  red: "#ef4444",
  violet: "#6366f1",
};

export function CashFlowChart({
  disbursed,
  recovered,
  outstanding,
}: {
  disbursed: number;
  recovered: number;
  outstanding: number;
}) {
  const data = [
    { name: "Disbursed", value: disbursed, fill: "#6366f1" },
    { name: "Recovered", value: recovered, fill: "#22c55e" },
    { name: "Outstanding", value: outstanding, fill: "#f59e0b" },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Portfolio Cash Flow</CardTitle>
      </CardHeader>
      <CardBody>
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={data} barSize={56}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
            <XAxis dataKey="name" tick={{ fontSize: 12, fill: "#64748b" }} axisLine={false} tickLine={false} />
            <YAxis
              tick={{ fontSize: 12, fill: "#64748b" }}
              axisLine={false}
              tickLine={false}
              tickFormatter={(v) => `₹${(v / 100000).toFixed(0)}L`}
            />
            <Tooltip
              formatter={(value) => formatCurrency(Number(value))}
              contentStyle={{ borderRadius: 12, border: "1px solid #e2e8f0", fontSize: 13 }}
            />
            <Bar dataKey="value" radius={[8, 8, 0, 0]}>
              {data.map((d) => (
                <Cell key={d.name} fill={d.fill} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </CardBody>
    </Card>
  );
}

export function StatusDistributionChart({ counts }: { counts: Record<string, number> }) {
  const data = Object.entries(counts)
    .filter(([, count]) => count > 0)
    .map(([status, count]) => ({
      name: LOAN_STATUS_META[status as keyof typeof LOAN_STATUS_META]?.label ?? status,
      value: count,
      fill: TONE_HEX[LOAN_STATUS_META[status as keyof typeof LOAN_STATUS_META]?.tone ?? "slate"],
    }));

  return (
    <Card>
      <CardHeader>
        <CardTitle>Loans by Status</CardTitle>
      </CardHeader>
      <CardBody>
        {data.length === 0 ? (
          <p className="py-16 text-center text-sm text-ink-400">No loan data yet</p>
        ) : (
          <ResponsiveContainer width="100%" height={280}>
            <PieChart>
              <Pie data={data} dataKey="value" nameKey="name" innerRadius={60} outerRadius={95} paddingAngle={2}>
                {data.map((d) => (
                  <Cell key={d.name} fill={d.fill} stroke="white" strokeWidth={2} />
                ))}
              </Pie>
              <Tooltip contentStyle={{ borderRadius: 12, border: "1px solid #e2e8f0", fontSize: 13 }} />
            </PieChart>
          </ResponsiveContainer>
        )}
        <div className="mt-2 flex flex-wrap justify-center gap-x-4 gap-y-1.5">
          {data.map((d) => (
            <span key={d.name} className="flex items-center gap-1.5 text-xs text-ink-500">
              <span className="size-2 rounded-full" style={{ backgroundColor: d.fill }} />
              {d.name} ({d.value})
            </span>
          ))}
        </div>
      </CardBody>
    </Card>
  );
}
