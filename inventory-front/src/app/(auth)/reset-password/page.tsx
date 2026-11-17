"use client";
import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Check, Eye, EyeOff } from "lucide-react";
import { toast } from "sonner";
import { auth } from "@/lib/api";
import { cn, errorMessage } from "@/lib/utils";
import { LogoMark } from "@/components/layout/Logo";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const rules = (p: string) => [
  { ok: p.length >= 8, label: "At least 8 characters" },
  { ok: /[A-Za-z]/.test(p) && /\d/.test(p), label: "Letters and numbers" },
];

function ResetForm() {
  const token = useSearchParams().get("token") ?? "";
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);

  const checks = rules(password);
  const valid = checks.every((c) => c.ok) && password === confirm;

  if (!token) {
    return (
      <div className="space-y-4 text-center">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Link incomplete</h1>
        <p className="text-sm text-ink-3">Open the link from the email again, or ask for a new one.</p>
        <Link href="/forgot-password" className="inline-block text-sm font-medium text-brand-deep hover:underline">Send a new link</Link>
      </div>
    );
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid || busy) return;
    setBusy(true);
    try {
      const r = await auth.resetPassword(token, password);
      toast.success(r.message);
      router.push("/login");
    } catch (err) {
      toast.error(errorMessage(err, "Could not change the password"));
      setBusy(false);
    }
  };

  return (
    <>
      <div className="space-y-1 text-center">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Choose a new password</h1>
        <p className="text-sm text-ink-3">You&apos;ll be signed out on every device and can sign in with the new one.</p>
      </div>
      <form onSubmit={submit} className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="rp-new">New password</Label>
          <div className="relative">
            <Input id="rp-new" type={show ? "text" : "password"} autoComplete="new-password" autoFocus
              value={password} onChange={(e) => setPassword(e.target.value)} className="pr-10" />
            <button type="button" aria-label={show ? "Hide password" : "Show password"} onClick={() => setShow((v) => !v)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-3 hover:text-ink">
              {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          <ul className="space-y-0.5 pt-1">
            {checks.map((c) => (
              <li key={c.label} className={cn("flex items-center gap-1.5 text-xs", c.ok ? "text-emerald-700" : "text-ink-3")}>
                <Check className={cn("h-3.5 w-3.5", !c.ok && "opacity-30")} /> {c.label}
              </li>
            ))}
          </ul>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="rp-confirm">Type it again</Label>
          <Input id="rp-confirm" type={show ? "text" : "password"} autoComplete="new-password"
            value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          {confirm && confirm !== password && <p className="text-xs text-red-600">The passwords don&apos;t match</p>}
        </div>
        <Button type="submit" className="w-full" disabled={!valid || busy}>{busy ? "Saving…" : "Save new password"}</Button>
      </form>
    </>
  );
}

/** Opened from the "Reset your password" email. */
export default function ResetPasswordPage() {
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex items-center gap-2 px-6 pt-6 md:px-10 md:pt-8">
        <LogoMark className="h-8 w-8" />
        <span className="text-xl font-semibold tracking-tight text-ink">Inventory</span>
      </header>
      <main className="flex flex-1 items-center justify-center px-4 py-8">
        <div className="w-full max-w-md space-y-6 rounded-4xl border border-white/70 bg-frame p-8 shadow-float">
          <Suspense fallback={null}><ResetForm /></Suspense>
        </div>
      </main>
    </div>
  );
}
