"use client";
import { useEffect, useState } from "react";
import { Check, Loader2, X } from "lucide-react";
import { auth } from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/** Same rules as the backend (Usernames.java). */
export function cleanUsername(raw: string) {
  return raw.trim().replace(/^@/, "").toLowerCase();
}

export function usernameProblem(u: string): string | null {
  if (u.length < 3 || u.length > 20) return "3 to 20 characters";
  if (!/^[a-z0-9_]+$/.test(u)) return "Use only letters, numbers and _";
  return null;
}

export type UsernameStatus = "idle" | "checking" | "ok" | "bad";

/**
 * A username box with an @ in front and a live "is it free?" check.
 * `current` is the person's own username (counts as free when editing a profile).
 */
export function UsernameField({ id = "username", value, onChange, current, onStatus }: {
  id?: string;
  value: string;
  onChange: (v: string) => void;
  current?: string;
  onStatus?: (s: UsernameStatus) => void;
}) {
  const [status, setStatus] = useState<UsernameStatus>("idle");
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const u = cleanUsername(value);
    const set = (s: UsernameStatus, m: string | null) => { setStatus(s); setMessage(m); onStatus?.(s); };
    if (!u) { set("idle", null); return; }
    if (current && u === current) { set("ok", null); return; }
    const problem = usernameProblem(u);
    if (problem) { set("bad", problem); return; }

    set("checking", null);
    let stale = false;
    const t = setTimeout(async () => {
      try {
        const r = await auth.usernameAvailable(u);
        if (!stale) set(r.available ? "ok" : "bad", r.available ? `@${r.username} is free` : (r.reason ?? "Not available"));
      } catch {
        // Couldn't reach the server: don't block the form, the server checks again on save.
        if (!stale) set("idle", "Couldn't check right now; we'll check when you continue.");
      }
    }, 400);
    return () => { stale = true; clearTimeout(t); };
    // onStatus is a callback from the parent; re-running on its identity would re-check needlessly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, current]);

  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>Username</Label>
      <div className="relative">
        <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-sm text-ink-3">@</span>
        <Input
          id={id}
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          placeholder="ama_mensah"
          value={value}
          onChange={(e) => onChange(e.target.value.replace(/\s/g, ""))}
          className="pl-7 pr-9"
          aria-invalid={status === "bad"}
          aria-describedby={`${id}-hint`}
        />
        <span className="absolute right-3 top-1/2 -translate-y-1/2" aria-hidden="true">
          {status === "checking" && <Loader2 className="h-4 w-4 animate-spin text-ink-3" />}
          {status === "ok" && <Check className="h-4 w-4 text-brand-deep" />}
          {status === "bad" && <X className="h-4 w-4 text-red-600" />}
        </span>
      </div>
      <p id={`${id}-hint`} className={status === "bad" ? "text-xs text-red-600" : "text-xs text-ink-3"} aria-live="polite">
        {message ?? "How people find you to invite you to their company."}
      </p>
    </div>
  );
}
