import { Outlet } from "react-router-dom";
import { Navbar } from "@/components/layout/Navbar";
import { useNotificationStream } from "@/hooks/useNotificationStream";

export function AppLayout() {
  useNotificationStream();

  return (
    <div className="min-h-screen bg-ink-50">
      <Navbar />
      <main className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <Outlet />
      </main>
    </div>
  );
}
