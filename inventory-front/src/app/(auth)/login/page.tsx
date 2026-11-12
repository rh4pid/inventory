"use client";
import { errorMessage } from "@/lib/utils";
import { LogoMark } from "@/components/layout/Logo";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { Eye, EyeOff } from "lucide-react";
import { useAuth } from "@/context/AuthProvider";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const schema = z.object({
  email:    z.string().email("Invalid email"),
  password: z.string().min(1, "Password required"),
});
type FormData = z.infer<typeof schema>;

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const [showPass, setShowPass] = useState(false);

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  });

  const onSubmit = async (data: FormData) => {
    try {
      const u = await login(data);
      router.push(u.superAdmin ? "/admin" : "/overview");
    } catch (err) {
      toast.error(errorMessage(err, "Login failed"));
    }
  };

  return (
    <div className="w-full max-w-md">
      <div className="space-y-6 rounded-4xl border border-white/70 bg-frame p-8 shadow-float">
        {/* Logo */}
        <div className="flex flex-col items-center gap-2 pb-2">
          <div className="flex items-center gap-2">
            <LogoMark className="h-9 w-9" />
            <span className="text-2xl font-semibold tracking-tight text-ink">Inventory</span>
          </div>
          <p className="text-sm text-gray-500">Sign in to your account</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="email">Email address</Label>
            <Input
              id="email"
              type="email"
              placeholder="you@company.com"
              {...register("email")}
            />
            {errors.email && (
              <p className="text-xs text-red-600">{errors.email.message}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="password">Password</Label>
            <div className="relative">
              <Input
                id="password"
                type={showPass ? "text" : "password"}
                placeholder="••••••••"
                {...register("password")}
                className="pr-10"
              />
              <button
                type="button"
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                onClick={() => setShowPass((v) => !v)}
              >
                {showPass ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
            {errors.password && (
              <p className="text-xs text-red-600">{errors.password.message}</p>
            )}
          </div>

          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? "Signing in…" : "Sign in"}
          </Button>
        </form>

        <p className="text-center text-sm text-gray-500">
          No account yet? Your company administrator creates it for you.
        </p>
      </div>
    </div>
  );
}
