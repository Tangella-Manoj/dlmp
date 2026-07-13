import { Navigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { CustomerDashboard } from "@/pages/CustomerDashboard";

/** Root route: officers/admins land on the review console, customers see their dashboard. */
export function HomePage() {
  const { session } = useAuth();
  const isOfficer = session?.role === "ROLE_ADMIN" || session?.role === "ROLE_LOAN_OFFICER";
  if (isOfficer) return <Navigate to="/admin/loans" replace />;
  return <CustomerDashboard />;
}
