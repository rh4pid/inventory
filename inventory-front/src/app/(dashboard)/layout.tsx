"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Sidebar, MobileNav } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { useAuth } from "@/context/AuthProvider";
import { WorkspaceProvider } from "@/context/WorkspaceContext";
import { ForcePasswordChange } from "@/components/account/ForcePasswordChange";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const { loading, user } = useAuth();
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    if (!loading && !user) router.push("/login");
    // The super admin manages companies from the admin console; they are not inside any company.
    else if (!loading && user?.superAdmin) router.replace("/admin");
  }, [loading, user, router]);

  if (loading) return <PageLoader />;
  if (!user || user.superAdmin) return null;
  if (user.mustChangePassword) return <ForcePasswordChange />;

  return (
    <WorkspaceProvider>
      <div className="app-canvas h-dvh p-0 md:p-4 lg:p-5">
        <div className="flex h-full overflow-hidden bg-frame md:rounded-4xl md:border md:border-white/70 md:shadow-float">
          <Sidebar />
          <MobileNav open={menuOpen} onClose={() => setMenuOpen(false)} />
          <div className="flex min-w-0 flex-1 flex-col">
            <Header onOpenMenu={() => setMenuOpen(true)} />
            <main className="min-h-0 flex-1 overflow-y-auto px-4 pb-6 pt-4 md:px-6">
              {children}
            </main>
          </div>
        </div>
      </div>
    </WorkspaceProvider>
  );
}
