"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { Building2, LogOut, UserPlus } from "lucide-react";
import { useAuth } from "@/context/AuthProvider";
import { LogoMark } from "@/components/layout/Logo";
import { Button } from "@/components/ui/button";
import { CreateOwnCompanyDialog } from "@/components/company/CreateOwnCompanyDialog";
import { PendingInvites } from "@/components/company/PendingInvites";

/** Signed in but not in any company yet: start one, or wait for an invite. */
export function NoCompany() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [creating, setCreating] = useState(false);

  return (
    <div className="app-canvas flex min-h-dvh flex-col">
      <header className="flex items-center justify-between px-6 pt-6 md:px-10">
        <div className="flex items-center gap-2">
          <LogoMark className="h-8 w-8" />
          <span className="text-xl font-semibold tracking-tight text-ink">Inventory</span>
        </div>
        <Button variant="ghost" size="sm" onClick={async () => { await logout(); router.push("/login"); }}>
          <LogOut className="mr-1.5 h-4 w-4" /> Sign out
        </Button>
      </header>

      <main className="flex flex-1 items-start justify-center px-4 py-10 md:items-center">
        <div className="w-full max-w-2xl space-y-5">
          <div className="text-center">
            <h1 className="text-2xl font-semibold tracking-tight text-ink">Welcome, {user?.firstName}</h1>
            <p className="mt-1 text-sm text-ink-3">You&apos;re not in a company yet. Start your own, or join one.</p>
          </div>

          <PendingInvites />

          <div className="grid gap-4 md:grid-cols-2">
            <section className="flex flex-col rounded-4xl border border-line/80 bg-surface p-6 shadow-card">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-brand-soft text-brand-deep"><Building2 className="h-5 w-5" /></span>
              <h2 className="mt-4 font-semibold text-ink">Start your own company</h2>
              <p className="mt-1 flex-1 text-sm text-ink-3">You&apos;ll be the owner: add branches, products and your team.</p>
              <Button className="mt-5" onClick={() => setCreating(true)}>Create company</Button>
            </section>
            <section className="flex flex-col rounded-4xl border border-line/80 bg-surface p-6 shadow-card">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-accent-soft text-accent-deep"><UserPlus className="h-5 w-5" /></span>
              <h2 className="mt-4 font-semibold text-ink">Join a company</h2>
              <p className="mt-1 flex-1 text-sm text-ink-3">Give your username to the company&apos;s owner or admin. Their invite will appear here.</p>
              {user?.username && (
                <div className="mt-5 rounded-2xl bg-surface-2 px-4 py-3 text-center font-mono text-lg font-semibold text-ink">@{user.username}</div>
              )}
            </section>
          </div>
        </div>
      </main>
      <CreateOwnCompanyDialog open={creating} onClose={() => setCreating(false)} />
    </div>
  );
}
