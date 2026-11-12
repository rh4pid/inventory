"use client";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthProvider";
import { LogoMark } from "@/components/layout/Logo";
import { ChangePasswordForm } from "./ChangePasswordForm";

/** Shown instead of the app until someone who signed in with a temporary password picks their own. */
export function ForcePasswordChange() {
  const { user, logout } = useAuth();
  const router = useRouter();

  return (
    <div className="app-canvas flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="w-full max-w-md space-y-6 rounded-4xl border border-white/70 bg-frame p-8 shadow-float">
        <div className="flex flex-col items-center gap-2 text-center">
          <LogoMark className="h-9 w-9" />
          <h1 className="text-xl font-semibold text-ink">Choose your password</h1>
          <p className="text-sm text-ink-3">
            Welcome{user?.firstName ? `, ${user.firstName}` : ""}. You signed in with a temporary password.
            Pick your own to continue.
          </p>
        </div>
        <ChangePasswordForm currentLabel="Temporary password" submitLabel="Save and continue" />
        <button
          onClick={async () => { await logout(); router.push("/login"); }}
          className="block w-full text-center text-sm text-ink-3 hover:text-ink"
        >
          Sign out
        </button>
      </div>
    </div>
  );
}
