"use client";
import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import Link from "next/link";
import { Lock } from "lucide-react";
import { useBusiness } from "@/context/BusinessContext";
import { useCan, pageNeed, ROLE_LABEL } from "@/lib/access";
import { Sidebar, MobileNav } from "@/components/layout/Sidebar";
import { Header } from "@/components/layout/Header";
import { BackButton } from "@/components/layout/BackButton";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { useAuth } from "@/context/AuthProvider";
import { WorkspaceProvider } from "@/context/WorkspaceContext";
import { SettingsProvider } from "@/context/SettingsContext";
import { ForcePasswordChange } from "@/components/account/ForcePasswordChange";
import { NoCompany } from "@/components/company/NoCompany";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const { loading, user } = useAuth();
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);
  const pathname = usePathname();
  const can = useCan();
  const { activeBusiness, businesses, businessesLoaded } = useBusiness();
  // Pages a role can't use show a message (the server refuses the data anyway).
  const allowed = !activeBusiness || can(pageNeed(pathname));

  useEffect(() => {
    if (!loading && !user) router.push("/login");
    // The super admin manages companies from the admin console; they are not inside any company.
    else if (!loading && user?.superAdmin) router.replace("/admin");
  }, [loading, user, router]);

  if (loading) return <PageLoader />;
  if (!user || user.superAdmin) return null;
  if (user.mustChangePassword) return <ForcePasswordChange />;
  if (!businessesLoaded) return <PageLoader />;
  if (businesses.length === 0) return <NoCompany />;

  return (
    <SettingsProvider>
    <WorkspaceProvider>
      <div className="h-dvh">
        <div className="flex h-full overflow-hidden bg-frame">
          <Sidebar />
          <MobileNav open={menuOpen} onClose={() => setMenuOpen(false)} />
          <div className="flex min-w-0 flex-1 flex-col">
            <Header onOpenMenu={() => setMenuOpen(true)} />
            <main className="min-h-0 flex-1 overflow-y-auto px-4 pb-6 pt-4 md:px-6">
              <BackButton />
              {allowed ? children : (
                <div className="mx-auto mt-16 max-w-md rounded-4xl border border-line/80 bg-surface p-8 text-center shadow-card">
                  <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-accent-soft text-accent-deep"><Lock className="h-5 w-5" /></span>
                  <h1 className="mt-4 text-lg font-semibold text-ink">Not available for your role</h1>
                  <p className="mt-1 text-sm text-ink-3">
                    As {ROLE_LABEL[activeBusiness?.role ?? ""] ?? "a team member"}, you don&apos;t have access to this page. Ask a manager if you need it.
                  </p>
                  <Link href="/overview" className="mt-5 inline-flex h-10 items-center rounded-xl bg-brand px-4 text-sm font-medium text-white hover:bg-brand-deep">Go to overview</Link>
                </div>
              )}
            </main>
          </div>
        </div>
      </div>
    </WorkspaceProvider>
    </SettingsProvider>
  );
}
