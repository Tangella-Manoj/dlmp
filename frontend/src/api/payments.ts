import { api } from "@/api/client";
import type { ApiResponse, Page } from "@/types/api";
import type { PaymentRequest, PaymentResponse } from "@/types/domain";

export const paymentsApi = {
  async initiate(payload: PaymentRequest, idempotencyKey: string) {
    const res = await api.post<ApiResponse<PaymentResponse>>(
      "/api/v1/payments/initiate",
      payload,
      { headers: { "X-Idempotency-Key": idempotencyKey } },
    );
    return res.data.data;
  },
  async byLoan(loanId: string, page = 0, size = 20) {
    const res = await api.get<ApiResponse<Page<PaymentResponse>>>(
      `/api/v1/payments/loan/${loanId}`,
      { params: { page, size } },
    );
    return res.data.data;
  },
  async byRef(reference: string) {
    const res = await api.get<ApiResponse<PaymentResponse>>(`/api/v1/payments/ref/${reference}`);
    return res.data.data;
  },
};
