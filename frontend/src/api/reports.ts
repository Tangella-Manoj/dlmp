import { api } from "@/api/client";
import type { ApiResponse, Page } from "@/types/api";
import type { LoanStatSnapshot } from "@/types/domain";

export interface PortfolioSummary {
  totalLoans: number;
  activeLoans: number;
  pendingLoans: number;
  totalDisbursed: number;
  totalRecovered: number;
}

export const reportsApi = {
  async portfolio() {
    const res = await api.get<ApiResponse<PortfolioSummary>>("/api/v1/reports/portfolio");
    return res.data.data;
  },
  async loans(status?: string, page = 0, size = 20) {
    const res = await api.get<ApiResponse<Page<LoanStatSnapshot>>>("/api/v1/reports/loans", {
      params: { status, page, size },
    });
    return res.data.data;
  },
};
