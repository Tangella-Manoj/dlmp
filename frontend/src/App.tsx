import { lazy, Suspense } from "react";
import { Routes, Route } from "react-router-dom";
import { AppLayout } from "@/components/layout/AppLayout";
import { ProtectedRoute, RoleRoute } from "@/routes/ProtectedRoute";
import { PageSpinner } from "@/components/ui/Spinner";
import { LoginPage } from "@/pages/Login";
import { RegisterPage } from "@/pages/Register";
import { HomePage } from "@/pages/Home";
import { ApplyLoanPage } from "@/pages/ApplyLoan";
import { VerifyIncomePage } from "@/pages/VerifyIncome";
import { LoanDetailPage } from "@/pages/LoanDetail";
import { NotificationsPage } from "@/pages/Notifications";
import { ProfilePage } from "@/pages/Profile";
import { AdminLoansPage } from "@/pages/AdminLoans";
import { NotFoundPage } from "@/pages/NotFound";

// Lazy-loaded: pulls in recharts, the heaviest dependency — kept out of the
// main bundle since most users (customers) never visit this page.
const AdminReportsPage = lazy(() =>
  import("@/pages/AdminReports").then((m) => ({ default: m.AdminReportsPage })),
);

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/loans/:loanId" element={<LoanDetailPage />} />

          <Route element={<RoleRoute allow={["ROLE_CUSTOMER"]} />}>
            <Route path="/loans/apply" element={<ApplyLoanPage />} />
            <Route path="/verify-income" element={<VerifyIncomePage />} />
          </Route>

          <Route element={<RoleRoute allow={["ROLE_ADMIN", "ROLE_LOAN_OFFICER"]} />}>
            <Route path="/admin/loans" element={<AdminLoansPage />} />
            <Route
              path="/admin/reports"
              element={
                <Suspense fallback={<PageSpinner />}>
                  <AdminReportsPage />
                </Suspense>
              }
            />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
