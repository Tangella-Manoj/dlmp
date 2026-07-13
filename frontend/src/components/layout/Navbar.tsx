import { useEffect, useRef, useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { LayoutDashboard, LogOut, Menu, ShieldCheck, User, Wallet, X } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { NotificationBell } from "@/components/layout/NotificationBell";
import { initials } from "@/lib/format";
import { cn } from "@/lib/utils";

const customerLinks = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard },
  { to: "/loans/apply", label: "Apply for Loan", icon: Wallet },
];

const officerLinks = [
  { to: "/admin/loans", label: "All Loans", icon: LayoutDashboard },
  { to: "/admin/reports", label: "Reports", icon: ShieldCheck },
];

export function Navbar() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const isOfficer = session?.role === "ROLE_ADMIN" || session?.role === "ROLE_LOAN_OFFICER";
  const links = isOfficer ? officerLinks : customerLinks;

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  async function handleLogout() {
    await logout();
    navigate("/login");
  }

  return (
    <header className="sticky top-0 z-30 border-b border-ink-200 bg-white/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <div className="flex items-center gap-8">
          <Link to="/" className="flex items-center gap-2">
            <div className="flex size-8 items-center justify-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 text-sm font-bold text-white">
              D
            </div>
            <span className="font-display text-lg font-bold text-ink-900">DLMP</span>
          </Link>
          <nav className="hidden items-center gap-1 md:flex">
            {links.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                end={to === "/"}
                className={({ isActive }) =>
                  cn(
                    "flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-brand-50 text-brand-700"
                      : "text-ink-600 hover:bg-ink-100 hover:text-ink-900",
                  )
                }
              >
                <Icon className="size-4" />
                {label}
              </NavLink>
            ))}
          </nav>
        </div>

        <div className="flex items-center gap-1">
          <NotificationBell />

          <div className="relative" ref={menuRef}>
            <button
              onClick={() => setMenuOpen((o) => !o)}
              className="ml-1 flex items-center gap-2 rounded-full py-1 pl-1 pr-2 hover:bg-ink-100"
            >
              <div className="flex size-8 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700">
                {initials(session?.firstName)}
              </div>
              <span className="hidden text-sm font-medium text-ink-700 sm:block">
                {session?.firstName}
              </span>
            </button>
            {menuOpen && (
              <div className="absolute right-0 z-40 mt-2 w-52 animate-slide-up rounded-xl bg-white py-1.5 shadow-popover ring-1 ring-ink-200">
                <div className="border-b border-ink-100 px-3.5 py-2.5">
                  <p className="truncate text-sm font-medium text-ink-900">{session?.email}</p>
                  <p className="text-xs text-ink-400">{roleLabel(session?.role)}</p>
                </div>
                <Link
                  to="/profile"
                  onClick={() => setMenuOpen(false)}
                  className="flex items-center gap-2 px-3.5 py-2 text-sm text-ink-700 hover:bg-ink-50"
                >
                  <User className="size-4" /> Profile
                </Link>
                <button
                  onClick={handleLogout}
                  className="flex w-full items-center gap-2 px-3.5 py-2 text-sm text-danger-600 hover:bg-danger-50"
                >
                  <LogOut className="size-4" /> Log out
                </button>
              </div>
            )}
          </div>

          <button
            className="ml-1 rounded-lg p-2 text-ink-500 hover:bg-ink-100 md:hidden"
            onClick={() => setMobileOpen((o) => !o)}
          >
            {mobileOpen ? <X className="size-5" /> : <Menu className="size-5" />}
          </button>
        </div>
      </div>

      {mobileOpen && (
        <nav className="border-t border-ink-100 px-4 py-3 md:hidden">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === "/"}
              onClick={() => setMobileOpen(false)}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium",
                  isActive ? "bg-brand-50 text-brand-700" : "text-ink-600",
                )
              }
            >
              <Icon className="size-4" />
              {label}
            </NavLink>
          ))}
        </nav>
      )}
    </header>
  );
}

function roleLabel(role?: string) {
  switch (role) {
    case "ROLE_ADMIN":
      return "Administrator";
    case "ROLE_LOAN_OFFICER":
      return "Loan Officer";
    default:
      return "Customer";
  }
}
