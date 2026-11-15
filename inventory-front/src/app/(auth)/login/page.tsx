"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { Eye, EyeOff, Loader2 } from "lucide-react";
import { errorMessage } from "@/lib/utils";
import { useAuth } from "@/context/AuthProvider";
import { AuthShell } from "@/components/auth/AuthShell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const schema = z.object({
  login:    z.string().trim().min(1, "Enter your email or username"),
  password: z.string().min(1, "Password required"),
});
type FormData = z.infer<typeof schema>;

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const [showPass, setShowPass] = useState(false);
  const loginRef = useRef<HTMLInputElement | null>(null);

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });
  const loginField = register("login");

  // Ready to type straight away.
  useEffect(() => { loginRef.current?.focus(); }, []);

  const onSubmit = async (data: FormData) => {
    try {
      const u = await login(data);
      router.push(u.superAdmin ? "/admin" : "/overview");
    } catch (err) {
      toast.error(errorMessage(err, "Login failed"));
    }
  };

  return (
    <AuthShell>
      <div className="space-y-7">
        <div className="space-y-1.5">
          <h1 className="text-[28px] font-semibold tracking-tight text-ink">Welcome back</h1>
          <p className="text-sm text-ink-3">Sign in to your company workspace</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="login">Email or username</Label>
            <Input
              id="login"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              placeholder="you@company.com or @username"
              className="h-11"
              aria-invalid={!!errors.login}
              {...loginField}
              ref={(el) => { loginField.ref(el); loginRef.current = el; }}
            />
            {errors.login && <p className="text-xs text-bad">{errors.login.message}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="password">Password</Label>
            <div className="relative">
              <Input
                id="password"
                type={showPass ? "text" : "password"}
                autoComplete="current-password"
                placeholder="••••••••"
                className="h-11 pr-10"
                aria-invalid={!!errors.password}
                {...register("password")}
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
            {errors.password && <p className="text-xs text-bad">{errors.password.message}</p>}
          </div>

          <Button type="submit" className="h-11 w-full text-[15px] transition-transform active:scale-[.99]" disabled={isSubmitting}>
            {isSubmitting ? <><Loader2 className="h-4 w-4 animate-spin" /> Signing in…</> : "Sign in"}
          </Button>
        </form>

        <p className="text-center text-sm text-ink-3">
          No account yet?{" "}
          <Link href="/register" className="font-semibold text-brand-deep hover:underline">Create one</Link>
        </p>
      </div>
    </AuthShell>
  );
}
