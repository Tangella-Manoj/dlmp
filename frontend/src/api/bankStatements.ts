import { api } from "@/api/client";
import type { ApiResponse } from "@/types/api";
import type { BankStatementAnalysis } from "@/types/domain";

export const bankStatementsApi = {
  async analyze(file: File) {
    const form = new FormData();
    form.append("file", file);
    const res = await api.post<ApiResponse<BankStatementAnalysis>>(
      "/api/v1/bank-statements/analyze",
      form,
      { headers: { "Content-Type": "multipart/form-data" } },
    );
    return res.data.data;
  },
  async latest() {
    const res = await api.get<ApiResponse<BankStatementAnalysis>>("/api/v1/bank-statements/latest");
    return res.data.data;
  },
};
