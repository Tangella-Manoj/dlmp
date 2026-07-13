import { api } from "@/api/client";
import type { ApiResponse } from "@/types/api";
import type { AuthResponse, UserResponse } from "@/types/domain";

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phoneNumber?: string;
  panNumber?: string;
  monthlyIncome?: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export const authApi = {
  async register(payload: RegisterRequest) {
    const res = await api.post<ApiResponse<AuthResponse>>("/api/v1/auth/register", payload);
    return res.data.data;
  },
  async login(payload: LoginRequest) {
    const res = await api.post<ApiResponse<AuthResponse>>("/api/v1/auth/login", payload);
    return res.data.data;
  },
  async logout() {
    await api.post<ApiResponse<null>>("/api/v1/auth/logout");
  },
  async me() {
    const res = await api.get<ApiResponse<UserResponse>>("/api/v1/users/me");
    return res.data.data;
  },
};
