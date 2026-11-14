"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { KeyRound, LogOut, ShieldCheck } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/context/AuthProvider";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { LogoMark, Wordmark } from "@/components/layout/Logo";
import { ForcePasswordChange } from "@/components/account/ForcePasswordChange";
import { ChangePasswordForm } from "@/components/account/ChangePasswordForm";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuLabel,
} from "@/components/ui/dropdown-menu";

/** The super admin's console: same look as the app, without company navigation. */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { loading, user, logout } = useAuth();
  const router = useRouter();
  const [pwOpen, setPwOpen] = useState(false);

  useEffect(() => {
    if (!loading && !user) router.push("/login");
    else if (!loading && user && !user.superAdmin) router.replace("/overview");
  }, [loading, user, router]);

  if (loading) return <PageLoader />;
  if (!user || !user.superAdmin) return null;
  if (user.mustChangePassword) return <ForcePasswordChange />;

  const signOut = async () => {
    await logout();
    toast.success("Signed out");
    router.push("/login");
  };

  return (
    <div className="h-dvh">
      <div className="flex h-full flex-col overflow-hidden bg-frame">
        <header className="flex shrink-0 items-center gap-3 px-4 pb-2 pt-4 md:px-8 md:pt-6">
          <LogoMark className="h-9 w-9" />
          <div className="min-w-0">
            <Wordmark className="text-[22px] md:text-[28px]" />
            <p className="-mt-0.5 flex items-center gap-1 text-xs text-ink-3">
              <ShieldCheck className="h-3.5 w-3.5 text-accent" /> Admin console
            </p>
          </div>
          <div className="ml-auto">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button aria-label="Your account" className="flex items-center gap-2.5 rounded-2xl border border-line bg-surface py-1.5 pl-1.5 pr-3 shadow-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand">
                  <Avatar className="h-8 w-8 rounded-xl">
                    <AvatarFallback className="rounded-xl bg-gradient-to-br from-accent to-accent-deep text-xs font-semibold text-on-accent">SA</AvatarFallback>
                  </Avatar>
                  <span className="hidden text-sm font-medium text-ink sm:inline">Super admin</span>
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56">
                <DropdownMenuLabel>
                  <div className="flex flex-col">
                    <span className="font-medium text-ink">Super admin</span>
                    <span className="text-xs font-normal text-ink-3">{user.email}</span>
                  </div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={() => setPwOpen(true)}>
                  <KeyRound className="mr-2 h-4 w-4" /> Change password
                </DropdownMenuItem>
                <DropdownMenuItem onClick={signOut} className="text-red-600 focus:bg-red-50 focus:text-red-600">
                  <LogOut className="mr-2 h-4 w-4" /> Sign out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </header>
        <main className="min-h-0 flex-1 overflow-y-auto px-4 pb-8 pt-4 md:px-8">{children}</main>
      </div>

      <Dialog open={pwOpen} onOpenChange={setPwOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader><DialogTitle>Change password</DialogTitle></DialogHeader>
          <p className="mb-2 text-sm text-ink-3">
            After this, the password in SUPER_ADMIN_CREDENTIALS.txt no longer works. Keep the new one safe.
          </p>
          <ChangePasswordForm onDone={() => setPwOpen(false)} />
        </DialogContent>
      </Dialog>
    </div>
  );
}
