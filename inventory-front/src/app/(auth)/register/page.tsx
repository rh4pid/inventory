"use client";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Eye, EyeOff } from "lucide-react";
import { errorMessage } from "@/lib/utils";
import { useAuth } from "@/context/AuthProvider";
import { LogoMark } from "@/components/layout/Logo";
import { UsernameField, cleanUsername, type UsernameStatus } from "@/components/account/UsernameField";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/** "Ama", "Mensah" -> "ama_mensah" (the server makes sure it's free). */
function suggestFrom(first: string, last: string) {
  const slug = (s: string) => s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase()
    .replace(/'/g, "").replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");
  return [slug(first), slug(last)].filter(Boolean).join("_").slice(0, 20).replace(/_+$/, "");
}

const passwordRules = (p: string) => [
  { ok: p.length >= 8, label: "At least 8 characters" },
  { ok: /[A-Za-z]/.test(p) && /\d/.test(p), label: "Letters and numbers" },
];

export default function RegisterPage() {
  const { signUp } = useAuth();
  const router = useRouter();
  const [f, setF] = useState({ firstName: "", lastName: "", email: "", telephonePrimary: "", password: "" });
  const [username, setUsername] = useState("");
  const [usernameTouched, setUsernameTouched] = useState(false);
  const [usernameStatus, setUsernameStatus] = useState<UsernameStatus>("idle");
  const [showPass, setShowPass] = useState(false);
  const [busy, setBusy] = useState(false);

  const setName = (key: "firstName" | "lastName", v: string) => {
    const next = { ...f, [key]: v };
    setF(next);
    if (!usernameTouched) setUsername(suggestFrom(next.firstName, next.lastName));
  };

  const checks = passwordRules(f.password);
  const valid = f.firstName.trim() && f.lastName.trim() && /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email.trim())
    && usernameStatus === "ok" && checks.every((c) => c.ok);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid || busy) return;
    setBusy(true);
    try {
      await signUp({ ...f, username: cleanUsername(username) });
      toast.success("Welcome! Your account is ready.");
      router.push("/overview");
    } catch (err) {
      toast.error(errorMessage(err, "Could not create your account"));
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
          <div className="space-y-1 text-center">
            <h1 className="text-2xl font-semibold tracking-tight text-ink">Create your account</h1>
            <p className="text-sm text-ink-3">One account for every company you work with</p>
          </div>

          <form onSubmit={submit} className="space-y-4" noValidate>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="firstName">First name</Label>
                <Input id="firstName" autoComplete="given-name" value={f.firstName} onChange={(e) => setName("firstName", e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="lastName">Last name</Label>
                <Input id="lastName" autoComplete="family-name" value={f.lastName} onChange={(e) => setName("lastName", e.target.value)} />
              </div>
            </div>

            <UsernameField
              value={username}
              onChange={(v) => { setUsernameTouched(true); setUsername(v); }}
              onStatus={setUsernameStatus}
            />

            <div className="space-y-1.5">
              <Label htmlFor="email">Email address</Label>
              <Input id="email" type="email" autoComplete="email" placeholder="you@example.com"
                value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="phone">Phone <span className="font-normal text-ink-3">(optional)</span></Label>
              <Input id="phone" type="tel" autoComplete="tel"
                value={f.telephonePrimary} onChange={(e) => setF({ ...f, telephonePrimary: e.target.value })} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <div className="relative">
                <Input id="password" type={showPass ? "text" : "password"} autoComplete="new-password" className="pr-10"
                  value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />
                <button type="button" onClick={() => setShowPass((s) => !s)} aria-label={showPass ? "Hide password" : "Show password"}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-3 hover:text-ink">
                  {showPass ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              <ul className="flex flex-wrap gap-x-4 gap-y-1 pt-1 text-xs">
                {checks.map((c) => (
                  <li key={c.label} className={c.ok ? "text-brand-deep" : "text-ink-3"}>{c.ok ? "✓" : "•"} {c.label}</li>
                ))}
              </ul>
            </div>

            <Button type="submit" className="w-full" disabled={!valid || busy}>
              {busy ? "Creating your account…" : "Create account"}
            </Button>
          </form>

          <p className="text-center text-sm text-ink-3">
            Already have an account?{" "}
            <Link href="/login" className="font-medium text-brand-deep hover:underline">Sign in</Link>
          </p>
        </div>
      </main>
    </div>
  );
}
