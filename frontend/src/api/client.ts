import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { tokenStore } from "@/lib/tokenStore";
import type { ApiResponse, ErrorResponse } from "@/types/api";
import type { AuthResponse } from "@/types/domain";

export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
});

api.interceptors.request.use((config) => {
  const session = tokenStore.get();
  if (session?.accessToken) {
    config.headers.set("Authorization", `Bearer ${session.accessToken}`);
  }
  return config;
});

// ─── Silent refresh-on-401, with a single in-flight refresh shared by
// every request that races into it (no thundering herd of refresh calls). ───
let refreshInFlight: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const session = tokenStore.get();
  if (!session?.refreshToken) throw new Error("No refresh token");

  const res = await axios.post<ApiResponse<AuthResponse>>(
    `${API_BASE_URL}/api/v1/auth/refresh`,
    null,
    { headers: { "X-Refresh-Token": session.refreshToken } },
  );
  const data = res.data.data;
  tokenStore.set({
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
    userId: data.userId,
    email: data.email,
    firstName: data.firstName,
    role: data.role,
  });
  return data.accessToken;
}

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as
      | (InternalAxiosRequestConfig & { _retried?: boolean })
      | undefined;

    const isAuthRoute = original?.url?.includes("/api/v1/auth/");
    if (error.response?.status === 401 && original && !original._retried && !isAuthRoute) {
      original._retried = true;
      try {
        refreshInFlight ??= refreshAccessToken().finally(() => {
          refreshInFlight = null;
        });
        const newToken = await refreshInFlight;
        original.headers.set("Authorization", `Bearer ${newToken}`);
        return api(original);
      } catch {
        tokenStore.clear();
        window.location.assign("/login");
      }
    }
    return Promise.reject(error);
  },
);

/** Extracts a human-readable message from a failed API call, backend-shape aware. */
export function apiErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as ErrorResponse | undefined;
    if (body?.fieldErrors?.length) {
      return body.fieldErrors.map((f) => f.message).join(", ");
    }
    if (body?.message) return body.message;
    if (err.response?.status === 403) return "You don't have permission to do that.";
    if (err.code === "ERR_NETWORK") return "Can't reach the server. It may be waking up — try again in a moment.";
  }
  return "Something went wrong. Please try again.";
}
