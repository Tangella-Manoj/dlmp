import { api } from "@/api/client";
import type { ApiResponse, Page } from "@/types/api";
import type {
  EmiScheduleResponse,
  LoanApplicationRequest,
  LoanDecisionRequest,
  LoanResponse,
  LoanStatus,
  PortfolioStats,
} from "@/types/domain";

export const loansApi = {
  async apply(payload: LoanApplicationRequest) {
    const res = await api.post<ApiResponse<LoanResponse>>("/api/v1/loans/apply", payload);
    return res.data.data;
  },
  async getById(loanId: string) {
    const res = await api.get<ApiResponse<LoanResponse>>(`/api/v1/loans/${loanId}`);
    return res.data.data;
  },
  async my(page = 0, size = 10) {
    const res = await api.get<ApiResponse<Page<LoanResponse>>>("/api/v1/loans/my", {
      params: { page, size },
    });
    return res.data.data;
  },
  async list(status?: LoanStatus, page = 0, size = 20) {
    const res = await api.get<ApiResponse<Page<LoanResponse>>>("/api/v1/loans", {
      params: { status, page, size },
    });
    return res.data.data;
  },
  async emiSchedule(loanId: string) {
    const res = await api.get<ApiResponse<EmiScheduleResponse[]>>(
      `/api/v1/loans/${loanId}/emi-schedule`,
    );
    return res.data.data;
  },
  async approve(loanId: string, payload: LoanDecisionRequest = {}) {
    const res = await api.put<ApiResponse<LoanResponse>>(
      `/api/v1/loans/${loanId}/approve`,
      payload,
    );
    return res.data.data;
  },
  async reject(loanId: string, payload: LoanDecisionRequest) {
    const res = await api.put<ApiResponse<LoanResponse>>(
      `/api/v1/loans/${loanId}/reject`,
      payload,
    );
    return res.data.data;
  },
  async disburse(loanId: string) {
    const res = await api.put<ApiResponse<LoanResponse>>(`/api/v1/loans/${loanId}/disburse`);
    return res.data.data;
  },
  async portfolioStats() {
    const res = await api.get<ApiResponse<PortfolioStats>>("/api/v1/loans/portfolio/stats");
    return res.data.data;
  },
};
