import { useState, useRef, useEffect } from "react";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { Bell, CheckCheck, Inbox } from "lucide-react";
import { notificationsApi } from "@/api/notifications";
import { timeAgo } from "@/lib/format";
import { cn } from "@/lib/utils";
import { Link } from "react-router-dom";

const typeDot: Record<string, string> = {
  LOAN: "bg-brand-500",
  PAYMENT: "bg-success-500",
  SECURITY: "bg-danger-500",
  SYSTEM: "bg-info-500",
};

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const qc = useQueryClient();

  const { data: unread = 0 } = useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: notificationsApi.unreadCount,
    refetchInterval: 20_000,
  });

  const { data: page } = useQuery({
    queryKey: ["notifications", "my"],
    queryFn: () => notificationsApi.my(0, 8),
    enabled: open,
  });

  const markAllRead = useMutation({
    mutationFn: notificationsApi.markAllRead,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen((o) => !o)}
        className="relative rounded-lg p-2 text-ink-500 hover:bg-ink-100 hover:text-ink-700"
        aria-label="Notifications"
      >
        <Bell className="size-5" />
        {unread > 0 && (
          <span className="absolute right-1 top-1 flex size-4 items-center justify-center rounded-full bg-danger-500 text-[10px] font-semibold text-white">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-2 w-80 animate-slide-up rounded-2xl bg-white shadow-popover ring-1 ring-ink-200">
          <div className="flex items-center justify-between border-b border-ink-100 px-4 py-3">
            <p className="text-sm font-semibold text-ink-900">Notifications</p>
            {unread > 0 && (
              <button
                onClick={() => markAllRead.mutate()}
                className="flex items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700"
              >
                <CheckCheck className="size-3.5" />
                Mark all read
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto scrollbar-thin">
            {!page || page.content.length === 0 ? (
              <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
                <Inbox className="size-8 text-ink-300" />
                <p className="text-sm text-ink-400">You're all caught up</p>
              </div>
            ) : (
              page.content.map((n) => (
                <div
                  key={n.id}
                  className={cn(
                    "flex gap-3 border-b border-ink-50 px-4 py-3 last:border-0",
                    !n.read && "bg-brand-50/40",
                  )}
                >
                  <span
                    className={cn(
                      "mt-1.5 size-1.5 shrink-0 rounded-full",
                      typeDot[n.notificationType ?? "SYSTEM"],
                    )}
                  />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-ink-800">{n.title}</p>
                    <p className="mt-0.5 line-clamp-2 text-xs text-ink-500">{n.message}</p>
                    <p className="mt-1 text-[11px] text-ink-400">{timeAgo(n.createdAt)}</p>
                  </div>
                </div>
              ))
            )}
          </div>
          <Link
            to="/notifications"
            onClick={() => setOpen(false)}
            className="block border-t border-ink-100 px-4 py-2.5 text-center text-sm font-medium text-brand-600 hover:bg-ink-50"
          >
            View all
          </Link>
        </div>
      )}
    </div>
  );
}
