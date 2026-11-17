"use client";
import { useState } from "react";
import Link from "next/link";
import { MailCheck } from "lucide-react";
import { toast } from "sonner";
import { auth } from "@/lib/api";
import { errorMessage } from "@/lib/utils";
import { LogoMark } from "@/components/layout/Logo";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/** "Forgot password": we email a reset link from the site (never from a company). */
export default function ForgotPasswordPage() {
  const [login, setLogin] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!login.trim() || busy) return;
    setBusy(true);
    try {
      const r = await auth.forgotPassword(login.trim());
      setSent(r.message);
    } catch (err) {
      toast.error(errorMessage(err, "Could not send the email"));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex items-center gap-2 px-6 pt-6 md:px-10 md:pt-8">
        <LogoMark className="h-8 w-8" />
        <span className="text-xl font-semibold tracking-tight text-ink">Inventory</span>
      </header>
      <main className="flex flex-1 items-center justify-center px-4 py-8">
        <div className="w-full max-w-md space-y-6 rounded-4xl border border-white/70 bg-frame p-8 shadow-float">
          {sent ? (
            <div className="space-y-4 text-center">
              <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-soft text-brand-deep">
                <MailCheck className="h-6 w-6" />
              </span>
              <h1 className="text-2xl font-semibold tracking-tight text-ink">Check your email</h1>
              <p className="text-sm text-ink-3">{sent}</p>
              <p className="text-sm text-ink-3">The link works once and expires in 30 minutes.</p>
              <Link href="/login" className="inline-block text-sm font-medium text-brand-deep hover:underline">Back to sign in</Link>
            </div>
          ) : (
            <>
              <div className="space-y-1 text-center">
                <h1 className="text-2xl font-semibold tracking-tight text-ink">Forgot your password?</h1>
                <p className="text-sm text-ink-3">Enter your email or username and we&apos;ll email you a link to choose a new one.</p>
              </div>
              <form onSubmit={submit} className="space-y-4">
                <div className="space-y-1.5">
                  <Label htmlFor="fp-login">Email or username</Label>
                  <Input id="fp-login" autoFocus autoComplete="username" autoCapitalize="none" spellCheck={false}
                    placeholder="you@company.com or @username" value={login} onChange={(e) => setLogin(e.target.value)} />
                </div>
                <Button type="submit" className="w-full" disabled={busy || !login.trim()}>
                  {busy ? "Sending…" : "Email me a reset link"}
                </Button>
              </form>
              <p className="text-center text-sm text-ink-3">
                Remembered it? <Link href="/login" className="font-medium text-brand-deep hover:underline">Sign in</Link>
              </p>
            </>
          )}
        </div>
      </main>
    </div>
  );
}
