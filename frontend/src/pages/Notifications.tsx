import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, CheckCheck, Inbox } from "lucide-react";
import { notificationsApi } from "@/api/notifications";
import { Card, CardBody } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { PageSpinner } from "@/components/ui/Spinner";
import { EmptyState } from "@/components/ui/EmptyState";
import { cn } from "@/lib/utils";
import { formatDateTime, timeAgo } from "@/lib/format";

const typeDot: Record<string, string> = {
  LOAN: "bg-brand-500",
  PAYMENT: "bg-success-500",
  SECURITY: "bg-danger-500",
  SYSTEM: "bg-info-500",
};

export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const qc = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["notifications", "my", page],
    queryFn: () => notificationsApi.my(page, 20),
    // useNotificationStream pushes new notifications instantly over SSE —
    // this is only a slow safety net for the rare gap after a reconnect.
    refetchInterval: 60_000,
  });

  const markRead = useMutation({
    mutationFn: notificationsApi.markRead,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  });

  const markAllRead = useMutation({
    mutationFn: notificationsApi.markAllRead,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  });

  if (isLoading) return <PageSpinner />;

  const items = data?.content ?? [];

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="flex items-center gap-2 font-display text-2xl font-bold text-ink-900">
          <Bell className="size-6 text-brand-600" /> Notifications
        </h1>
        <Button variant="outline" size="sm" onClick={() => markAllRead.mutate()}>
          <CheckCheck className="size-4" /> Mark all read
        </Button>
      </div>

      {items.length === 0 ? (
        <EmptyState icon={Inbox} title="No notifications" description="You're all caught up." />
      ) : (
        <Card>
          <CardBody className="divide-y divide-ink-100 p-0">
            {items.map((n) => (
              <div
                key={n.id}
                onClick={() => !n.read && markRead.mutate(n.id)}
                className={cn(
                  "flex cursor-pointer gap-3 px-5 py-4 hover:bg-ink-50",
                  !n.read && "bg-brand-50/30",
                )}
              >
                <span
                  className={cn(
                    "mt-1.5 size-2 shrink-0 rounded-full",
                    typeDot[n.notificationType ?? "SYSTEM"],
                  )}
                />
                <div className="min-w-0 flex-1">
                  <div className="flex items-baseline justify-between gap-2">
                    <p className="text-sm font-semibold text-ink-800">{n.title}</p>
                    <span className="shrink-0 text-xs text-ink-400" title={formatDateTime(n.createdAt)}>
                      {timeAgo(n.createdAt)}
                    </span>
                  </div>
                  <p className="mt-0.5 text-sm text-ink-500">{n.message}</p>
                </div>
              </div>
            ))}
          </CardBody>
        </Card>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <Button variant="outline" size="sm" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <span className="text-sm text-ink-500">
            Page {data.number + 1} of {data.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
