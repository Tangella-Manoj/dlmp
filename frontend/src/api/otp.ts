import { api } from "@/api/client";
import type { ApiResponse } from "@/types/api";
import type { OtpPurpose } from "@/types/domain";

export const otpApi = {
  async request(purpose: OtpPurpose) {
    await api.post<ApiResponse<null>>("/api/v1/auth/otp/request", { purpose });
  },
  async verify(purpose: OtpPurpose, code: string) {
    await api.post<ApiResponse<null>>("/api/v1/auth/otp/verify", { purpose, code });
  },
};
