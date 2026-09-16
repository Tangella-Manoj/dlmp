import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import toast from "react-hot-toast";
import { tokenStore } from "@/lib/tokenStore";
import type { ApiResponse, ErrorResponse } from "@/types/api";
import type { AuthResponse } from "@/types/domain";

export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8090";

// Generous but bounded: Render's free tier can take up to ~3-4 min to boot a
// fully-cold JVM instance. A keep-warm ping every 10 min (see
// .github/workflows/keep-warm.yml) makes that rare in practice, but a request
// should still fail cleanly with a clear message rather than hang forever.
const REQUEST_TIMEOUT_MS = 90_000;

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
  timeout: REQUEST_TIMEOUT_MS,
});

// ─── Cold-start feedback: if a request is still pending after a few seconds,
// tell the user why instead of leaving them staring at a blank spinner. ───
type SlowTrackedConfig = InternalAxiosRequestConfig & {
  _slowTimer?: ReturnType<typeof setTimeout>;
  _countedAsSlow?: boolean;
};

const COLD_START_TOAST_ID = "cold-start-notice";
const SLOW_REQUEST_THRESHOLD_MS = 4000;
let pendingSlowRequests = 0;

function startSlowTracking(config: SlowTrackedConfig) {
  config._slowTimer = setTimeout(() => {
    config._countedAsSlow = true;
    pendingSlowRequests++;
    toast.loading(
      "Waking up the server — free-tier services sleep when idle. This can take up to a minute.",
      { id: COLD_START_TOAST_ID, duration: REQUEST_TIMEOUT_MS },
    );
  }, SLOW_REQUEST_THRESHOLD_MS);
}

function stopSlowTracking(config?: SlowTrackedConfig | null) {
  if (!config) return;
  clearTimeout(config._slowTimer);
  if (config._countedAsSlow) {
    pendingSlowRequests = Math.max(0, pendingSlowRequests - 1);
    if (pendingSlowRequests === 0) toast.dismiss(COLD_START_TOAST_ID);
  }
}

api.interceptors.request.use((config) => {
  const session = tokenStore.get();
  if (session?.accessToken) {
    config.headers.set("Authorization", `Bearer ${session.accessToken}`);
  }
  startSlowTracking(config as SlowTrackedConfig);
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
    { headers: { "X-Refresh-Token": session.refreshToken }, timeout: REQUEST_TIMEOUT_MS },
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
  (res) => {
    stopSlowTracking(res.config as SlowTrackedConfig);
    return res;
  },
  async (error: AxiosError) => {
    const original = error.config as
      | (SlowTrackedConfig & { _retried?: boolean })
      | undefined;
    stopSlowTracking(original);

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
    const data = err.response?.data;
    if (typeof data === "string" && data.trim()) {
      return data;
    }
    const body = data as (ErrorResponse & { error?: string }) | undefined;
    if (body?.fieldErrors?.length) {
      return body.fieldErrors.map((f) => f.message).join(", ");
    }
    if (body?.message && typeof body.message === "string" && body.message.trim()) {
      return body.message;
    }
    if (body?.error && typeof body.error === "string" && body.error.trim()) {
      return body.error;
    }
    if (err.response?.status === 401) return "Invalid email or password.";
    if (err.response?.status === 403) return "You don't have permission to do that.";
    if (err.response?.status === 404) return "Requested resource not found.";
    if (err.response?.status === 423) return "Account is temporarily locked. Please try again later.";
    if (err.code === "ECONNABORTED") {
      return "The server took too long to respond. It may still be waking up — please try again.";
    }
    if (err.code === "ERR_NETWORK") return "Can't reach the server. It may be waking up — try again in a moment.";
  }
  return "Something went wrong. Please try again.";
}
