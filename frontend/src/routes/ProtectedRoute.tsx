import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import type { UserRole } from "@/types/domain";

export function ProtectedRoute() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}

/** Gates a subtree to specific roles; anyone else is redirected home. */
export function RoleRoute({ allow }: { allow: UserRole[] }) {
  const { session } = useAuth();
  if (!session || !allow.includes(session.role)) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
