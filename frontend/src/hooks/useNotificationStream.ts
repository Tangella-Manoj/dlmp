import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { useAuth } from "@/context/AuthContext";
import { API_BASE_URL } from "@/api/client";
import type { Notification } from "@/types/domain";
import type { Page } from "@/types/api";

/**
 * Live push channel for in-app notifications (Server-Sent Events), replacing
 * the previous "wait for the next poll" model. The browser's native
 * EventSource auto-reconnects on transient drops using the same URL/token;
 * this effect only needs to re-establish the connection when the access
 * token itself rotates (silent refresh) or the user logs out.
 *
 * The gateway accepts the token via `?access_token=` for this path
 * specifically, since EventSource cannot set an Authorization header.
 */
export function useNotificationStream() {
  const { session } = useAuth();
  const qc = useQueryClient();

  useEffect(() => {
    if (!session?.accessToken) return;

    const url = `${API_BASE_URL}/api/v1/notifications/stream?access_token=${encodeURIComponent(session.accessToken)}`;
    const es = new EventSource(url);

    es.addEventListener("notification", (event) => {
      let notification: Notification;
      try {
        notification = JSON.parse((event as MessageEvent).data);
      } catch {
        return;
      }

      qc.setQueryData<number>(["notifications", "unread-count"], (prev) => (prev ?? 0) + 1);
      qc.setQueriesData<Page<Notification>>({ queryKey: ["notifications", "my"] }, (prev) =>
        prev ? { ...prev, content: [notification, ...prev.content].slice(0, prev.size || 20) } : prev,
      );
      toast(notification.title, { icon: "🔔" });

      // A notification means something concrete changed elsewhere (a loan
      // status flip, a payment posting) — refresh any page showing it right
      // now instead of waiting for that page's own poll interval.
      if (notification.notificationType === "LOAN" || notification.notificationType === "PAYMENT") {
        qc.invalidateQueries({ queryKey: ["loans"] });
        qc.invalidateQueries({ queryKey: ["reports"] });
        qc.invalidateQueries({ queryKey: ["payments"] });
      }
    });

    return () => es.close();
  }, [session?.accessToken, qc]);
}
