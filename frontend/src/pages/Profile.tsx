import { useQuery } from "@tanstack/react-query";
import { Mail, Phone, CreditCard, Calendar, ShieldCheck } from "lucide-react";
import { authApi } from "@/api/auth";
import { Card, CardBody, CardHeader, CardTitle } from "@/components/ui/Card";
import { PageSpinner } from "@/components/ui/Spinner";
import { Badge } from "@/components/ui/Badge";
import { formatCurrency, formatDateTime, initials } from "@/lib/format";

export function ProfilePage() {
  const { data: user, isLoading } = useQuery({
    queryKey: ["users", "me"],
    queryFn: authApi.me,
  });

  if (isLoading) return <PageSpinner />;
  if (!user) return null;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center gap-4">
        <div className="flex size-16 items-center justify-center rounded-full bg-brand-100 text-xl font-bold text-brand-700">
          {initials(user.firstName, user.lastName)}
        </div>
        <div>
          <h1 className="font-display text-2xl font-bold text-ink-900">
            {user.firstName} {user.lastName}
          </h1>
          <Badge className="mt-1 bg-brand-50 text-brand-700 ring-brand-500/20">
            {roleLabel(user.role)}
          </Badge>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Account Details</CardTitle>
        </CardHeader>
        <CardBody className="space-y-4">
          <Row icon={Mail} label="Email" value={user.email} />
          {user.phoneNumber && <Row icon={Phone} label="Phone" value={user.phoneNumber} />}
          {user.panNumber && <Row icon={CreditCard} label="PAN" value={user.panNumber} />}
          {user.monthlyIncome !== undefined && user.monthlyIncome !== null && (
            <Row icon={CreditCard} label="Monthly Income" value={formatCurrency(user.monthlyIncome)} />
          )}
          <Row icon={ShieldCheck} label="Status" value={user.status} />
          <Row icon={Calendar} label="Member since" value={formatDateTime(user.createdAt)} />
          {user.lastLogin && <Row icon={Calendar} label="Last login" value={formatDateTime(user.lastLogin)} />}
        </CardBody>
      </Card>
    </div>
  );
}

function Row({ icon: Icon, label, value }: { icon: typeof Mail; label: string; value: string }) {
  return (
    <div className="flex items-center gap-3 border-b border-ink-100 pb-4 last:border-0 last:pb-0">
      <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-ink-100">
        <Icon className="size-4 text-ink-500" />
      </div>
      <div>
        <p className="text-xs text-ink-400">{label}</p>
        <p className="text-sm font-medium text-ink-800">{value}</p>
      </div>
    </div>
  );
}

function roleLabel(role: string) {
  switch (role) {
    case "ROLE_ADMIN":
      return "Administrator";
    case "ROLE_LOAN_OFFICER":
      return "Loan Officer";
    default:
      return "Customer";
  }
}
