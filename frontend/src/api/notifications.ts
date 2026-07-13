import { api } from "@/api/client";
import type { ApiResponse, Page } from "@/types/api";
import type { Notification } from "@/types/domain";

export const notificationsApi = {
  async my(page = 0, size = 20) {
    const res = await api.get<ApiResponse<Page<Notification>>>("/api/v1/notifications/my", {
      params: { page, size },
    });
    return res.data.data;
  },
  async unreadCount() {
    const res = await api.get<ApiResponse<{ unread: number }>>(
      "/api/v1/notifications/unread-count",
    );
    return res.data.data.unread;
  },
  async markRead(id: string) {
    await api.put<ApiResponse<null>>(`/api/v1/notifications/${id}/read`);
  },
  async markAllRead() {
    const res = await api.put<ApiResponse<{ updated: number }>>(
      "/api/v1/notifications/read-all",
    );
    return res.data.data.updated;
  },
};
