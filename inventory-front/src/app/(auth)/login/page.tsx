"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { Eye, EyeOff, FastForward } from "lucide-react";
import { errorMessage } from "@/lib/utils";
import { useAuth } from "@/context/AuthProvider";
import { LogoMark } from "@/components/layout/Logo";
import { WarehouseScene } from "@/components/auth/WarehouseScene";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

/** When the box has landed on the stack (keep in step with the lg-* timings in globals.css). */
const LOGIN_INTRO_MS = 4600;

const schema = z.object({
  login:    z.string().trim().min(1, "Enter your email or username"),
  password: z.string().min(1, "Password required"),
});
type FormData = z.infer<typeof schema>;

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const [showPass, setShowPass] = useState(false);
  // The intro plays, then the card appears; skipping (click, key, button) jumps to the end.
  const [skipped, setSkipped] = useState(false);
  const [cardOpen, setCardOpen] = useState(false);
  const emailRef = useRef<HTMLInputElement | null>(null);

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });
  const loginField = register("login");

  const finish = useCallback(() => { setSkipped(true); setCardOpen(true); }, []);

  useEffect(() => {
    // People who ask their system for less motion get the finished scene straight away.
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) { finish(); return; }
    const t = setTimeout(() => setCardOpen(true), LOGIN_INTRO_MS);
    const onKey = () => finish();
    window.addEventListener("keydown", onKey, { once: true });
    return () => { clearTimeout(t); window.removeEventListener("keydown", onKey); };
  }, [finish]);

  useEffect(() => {
    if (cardOpen) setTimeout(() => emailRef.current?.focus(), 60);
  }, [cardOpen]);

  const onSubmit = async (data: FormData) => {
    try {
      const u = await login(data);
      router.push(u.superAdmin ? "/admin" : "/overview");
    } catch (err) {
      toast.error(errorMessage(err, "Login failed"));
    }
  };

  return (
    <div className="relative flex min-h-dvh flex-col">
      {/* Brand, top-left */}
      <header className="relative z-10 flex items-center gap-2 px-6 pt-6 md:px-10 md:pt-8">
        <LogoMark className="h-8 w-8" />
        <span className="text-xl font-semibold tracking-tight text-ink">Inventory</span>
      </header>

      {/* The scene */}
      <main
        className="flex flex-1 items-center justify-center px-2 pb-6 md:px-10"
        onClick={() => !cardOpen && finish()}
      >
        <div className="w-full max-w-[1100px]">
          <WarehouseScene skip={skipped} />
        </div>
      </main>

      {!cardOpen && (
        <button
          onClick={finish}
          className="absolute bottom-6 right-6 z-10 inline-flex items-center gap-1.5 rounded-xl border border-line bg-surface/80 px-3 py-2 text-sm font-medium text-ink-2 shadow-card backdrop-blur hover:text-ink"
        >
          <FastForward className="h-4 w-4" /> Skip intro
        </button>
      )}

      {/* The login card, once the box is on the stack */}
      {cardOpen && (
        <div className="lg-veil absolute inset-0 z-20 flex items-center justify-center bg-frame/35 px-4 backdrop-blur-[2px]">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="login-title"
            className="lg-card w-full max-w-md space-y-6 rounded-4xl border border-white/70 bg-frame p-8 shadow-float"
          >
            <div className="flex flex-col items-center gap-2 pb-1 text-center">
              <LogoMark className="h-10 w-10" />
              <h1 id="login-title" className="text-2xl font-semibold tracking-tight text-ink">Welcome back</h1>
              <p className="text-sm text-ink-3">Sign in to keep your stock moving</p>
            </div>

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="login">Email or username</Label>
                <Input
                  id="login"
                  autoComplete="username"
                  autoCapitalize="none"
                  spellCheck={false}
                  placeholder="you@company.com or @username"
                  {...loginField}
                  ref={(el) => { loginField.ref(el); emailRef.current = el; }}
                />
                {errors.login && <p className="text-xs text-red-600">{errors.login.message}</p>}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="password">Password</Label>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPass ? "text" : "password"}
                    autoComplete="current-password"
                    placeholder="••••••••"
                    {...register("password")}
                    className="pr-10"
                  />
                  <button
                    type="button"
                    aria-label={showPass ? "Hide password" : "Show password"}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-3 hover:text-ink"
                    onClick={() => setShowPass((v) => !v)}
                  >
                    {showPass ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {errors.password && <p className="text-xs text-red-600">{errors.password.message}</p>}
              </div>

              <Button type="submit" className="w-full" disabled={isSubmitting}>
                {isSubmitting ? "Signing in…" : "Sign in"}
              </Button>
            </form>

            <p className="text-center text-sm text-ink-3">
              No account yet?{" "}
              <Link href="/register" className="font-medium text-brand-deep hover:underline">Create one</Link>
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
