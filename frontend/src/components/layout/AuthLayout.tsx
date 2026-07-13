import type { ReactNode } from "react";
import { ShieldCheck, TrendingUp, Zap } from "lucide-react";

const features = [
  { icon: Zap, title: "Instant credit decisions", desc: "CIBIL-style scoring evaluates every application in real time." },
  { icon: ShieldCheck, title: "Bank-grade security", desc: "JWT auth, role-based access, and idempotent payments by design." },
  { icon: TrendingUp, title: "Live portfolio insight", desc: "Event-driven reporting keeps disbursement and recovery numbers current." },
];

export function AuthLayout({ children, title, subtitle }: { children: ReactNode; title: string; subtitle: string }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <div className="relative hidden flex-col justify-between overflow-hidden bg-ink-950 p-12 text-white lg:flex">
        <div
          className="pointer-events-none absolute inset-0 opacity-40"
          style={{
            backgroundImage:
              "radial-gradient(circle at 20% 20%, rgba(99,102,241,0.35), transparent 40%), radial-gradient(circle at 80% 70%, rgba(129,140,248,0.25), transparent 45%)",
          }}
        />
        <div className="relative flex items-center gap-2">
          <div className="flex size-9 items-center justify-center rounded-lg bg-gradient-to-br from-brand-400 to-brand-600 text-base font-bold">
            D
          </div>
          <span className="font-display text-xl font-bold">DLMP</span>
        </div>

        <div className="relative space-y-10">
          <div>
            <h1 className="font-display text-4xl font-bold leading-tight">
              Distributed Loan
              <br />
              Management Platform
            </h1>
            <p className="mt-4 max-w-md text-ink-300">
              A microservices-based lending system with SAGA orchestration, CQRS reporting, and a
              transactional outbox — built for correctness under failure.
            </p>
          </div>
          <div className="space-y-5">
            {features.map(({ icon: Icon, title, desc }) => (
              <div key={title} className="flex gap-3">
                <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-white/10">
                  <Icon className="size-4 text-brand-300" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-white">{title}</p>
                  <p className="text-sm text-ink-400">{desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>

        <p className="relative text-xs text-ink-500">© {new Date().getFullYear()} DLMP Platform</p>
      </div>

      <div className="flex items-center justify-center bg-ink-50 px-4 py-12 sm:px-6">
        <div className="w-full max-w-sm">
          <div className="mb-8 lg:hidden">
            <div className="mb-4 flex items-center gap-2">
              <div className="flex size-8 items-center justify-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 text-sm font-bold text-white">
                D
              </div>
              <span className="font-display text-lg font-bold text-ink-900">DLMP</span>
            </div>
          </div>
          <h2 className="font-display text-2xl font-bold text-ink-900">{title}</h2>
          <p className="mt-1.5 text-sm text-ink-500">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </div>
    </div>
  );
}
