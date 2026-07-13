import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { authApi, type LoginRequest, type RegisterRequest } from "@/api/auth";
import { tokenStore, type StoredSession } from "@/lib/tokenStore";
import { apiErrorMessage } from "@/api/client";

interface AuthContextValue {
  session: StoredSession | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (payload: LoginRequest) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<StoredSession | null>(tokenStore.get());
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => tokenStore.subscribe(setSession), []);

  async function login(payload: LoginRequest) {
    setIsLoading(true);
    try {
      const data = await authApi.login(payload);
      tokenStore.set({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        userId: data.userId,
        email: data.email,
        firstName: data.firstName,
        role: data.role,
      });
    } catch (err) {
      throw new Error(apiErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }

  async function register(payload: RegisterRequest) {
    setIsLoading(true);
    try {
      const data = await authApi.register(payload);
      tokenStore.set({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        userId: data.userId,
        email: data.email,
        firstName: data.firstName,
        role: data.role,
      });
    } catch (err) {
      throw new Error(apiErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }

  async function logout() {
    try {
      await authApi.logout();
    } catch {
      // best-effort — clear local session regardless
    }
    tokenStore.clear();
  }

  return (
    <AuthContext.Provider
      value={{ session, isAuthenticated: !!session, isLoading, login, register, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}
