"use client";
import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { toast } from "sonner";
import { users } from "@/lib/api";
import { useAuth } from "@/context/AuthProvider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const rules = (p: string) => [
  { ok: p.length >= 8, label: "At least 8 characters" },
  { ok: /[A-Za-z]/.test(p) && /\d/.test(p), label: "Letters and numbers" },
];

/** Change your own password. Used on the forced first-sign-in screen and in Profile. */
export function ChangePasswordForm({ currentLabel = "Current password", submitLabel = "Change password", onDone }: {
  currentLabel?: string;
  submitLabel?: string;
  onDone?: () => void;
}) {
  const { setUser } = useAuth();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const checks = rules(next);
  const valid = current && checks.every((c) => c.ok) && next === confirm && next !== current;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid) return;
    setBusy(true);
    try {
      const u = await users.changePassword(current, next);
      setUser(u);
      toast.success("Password changed");
      setCurrent(""); setNext(""); setConfirm("");
      onDone?.();
    } catch (err: any) {
      toast.error(err.message ?? "Could not change password");
    } finally {
      setBusy(false);
    }
  };

  const type = show ? "text" : "password";
  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="space-y-1.5">
        <Label htmlFor="cp-current">{currentLabel}</Label>
        <Input id="cp-current" type={type} autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="cp-new">New password</Label>
        <div className="relative">
          <Input id="cp-new" type={type} autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} className="pr-10" />
          <button type="button" onClick={() => setShow((s) => !s)} aria-label={show ? "Hide passwords" : "Show passwords"}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-3 hover:text-ink">
            {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        </div>
        <ul className="flex flex-wrap gap-x-4 gap-y-1 pt-1 text-xs">
          {checks.map((c) => (
            <li key={c.label} className={c.ok ? "text-brand-deep" : "text-ink-3"}>{c.ok ? "✓" : "•"} {c.label}</li>
          ))}
        </ul>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="cp-confirm">Confirm new password</Label>
        <Input id="cp-confirm" type={type} autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        {confirm && confirm !== next && <p className="text-xs text-red-600">Passwords don&apos;t match</p>}
        {next && current && next === current && <p className="text-xs text-red-600">Choose a different password</p>}
      </div>
      <Button type="submit" className="w-full" disabled={!valid || busy}>{busy ? "Saving…" : submitLabel}</Button>
    </form>
  );
}
