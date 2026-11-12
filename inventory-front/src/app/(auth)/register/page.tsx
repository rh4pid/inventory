"use client";
import { LogoMark } from "@/components/layout/Logo";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { Eye, EyeOff, Upload } from "lucide-react";
import { auth } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const step1Schema = z.object({
  firstName: z.string().min(1, "Required"),
  lastName:  z.string().min(1, "Required"),
  email:     z.string().email("Invalid email"),
  password:  z.string().min(8, "Minimum 8 characters"),
  telephonePrimary: z.string().optional(),
});

const step2Schema = z.object({
  businessName:    z.string().min(1, "Required"),
  businessEmail:   z.string().email("Invalid email").min(1, "Required"),
  businessContact: z.string().min(1, "Required"),
});

type Step1 = z.infer<typeof step1Schema>;
type Step2 = z.infer<typeof step2Schema>;

export default function RegisterPage() {
  const router = useRouter();
  const [step, setStep] = useState<1 | 2>(1);
  const [step1Data, setStep1Data] = useState<Step1 | null>(null);
  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [showPass, setShowPass] = useState(false);

  const form1 = useForm<Step1>({ resolver: zodResolver(step1Schema) });
  const form2 = useForm<Step2>({ resolver: zodResolver(step2Schema) });

  const onStep1 = (data: Step1) => {
    setStep1Data(data);
    setStep(2);
  };

  const onStep2 = async (data: Step2) => {
    if (!step1Data) return;
    const fd = new FormData();
    const payload = {
      firstName: step1Data.firstName,
      lastName:  step1Data.lastName,
      email:     step1Data.email,
      password:  step1Data.password,
      telephonePrimary: step1Data.telephonePrimary,
      businessName: data.businessName,
      businessEmail: data.businessEmail || undefined,
      businessContact: data.businessContact,
    };
    fd.append("data", new Blob([JSON.stringify(payload)], { type: "application/json" }));
    if (logoFile) fd.append("businessLogo", logoFile);

    try {
      await auth.register(fd);
      toast.success("Account created! Please sign in.");
      router.push("/login");
    } catch (err: any) {
      toast.error(err?.message ?? "Registration failed");
    }
  };

  return (
    <div className="w-full max-w-md">
      <div className="space-y-6 rounded-4xl border border-white/70 bg-frame p-8 shadow-float">
        <div className="flex flex-col items-center gap-2 pb-2">
          <div className="flex items-center gap-2">
            <LogoMark className="h-9 w-9" />
            <span className="text-2xl font-semibold tracking-tight text-ink">Inventory</span>
          </div>
          <p className="text-sm text-gray-500">
            {step === 1 ? "Create your account" : "Set up your business"}
          </p>
          {/* Step dots */}
          <div className="flex gap-2 mt-1">
            {[1, 2].map((s) => (
              <div key={s} className={`h-2 w-8 rounded-full ${step >= s ? "bg-blue-600" : "bg-gray-200"}`} />
            ))}
          </div>
        </div>

        {step === 1 && (
          <form onSubmit={form1.handleSubmit(onStep1)} className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>First name</Label>
                <Input placeholder="John" {...form1.register("firstName")} />
                {form1.formState.errors.firstName && (
                  <p className="text-xs text-red-600">{form1.formState.errors.firstName.message}</p>
                )}
              </div>
              <div className="space-y-1.5">
                <Label>Last name</Label>
                <Input placeholder="Doe" {...form1.register("lastName")} />
                {form1.formState.errors.lastName && (
                  <p className="text-xs text-red-600">{form1.formState.errors.lastName.message}</p>
                )}
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>Email</Label>
              <Input type="email" placeholder="you@company.com" {...form1.register("email")} />
              {form1.formState.errors.email && (
                <p className="text-xs text-red-600">{form1.formState.errors.email.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label>Password</Label>
              <div className="relative">
                <Input
                  type={showPass ? "text" : "password"}
                  placeholder="Min. 8 characters"
                  {...form1.register("password")}
                  className="pr-10"
                />
                <button type="button"
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  onClick={() => setShowPass((v) => !v)}>
                  {showPass ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {form1.formState.errors.password && (
                <p className="text-xs text-red-600">{form1.formState.errors.password.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label>Phone (optional)</Label>
              <Input type="tel" placeholder="+1 234 567 8900" {...form1.register("telephonePrimary")} />
            </div>
            <Button type="submit" className="w-full">Continue</Button>
          </form>
        )}

        {step === 2 && (
          <form onSubmit={form2.handleSubmit(onStep2)} className="space-y-4">
            <div className="space-y-1.5">
              <Label>Business name</Label>
              <Input placeholder="Acme Corp" {...form2.register("businessName")} />
              {form2.formState.errors.businessName && (
                <p className="text-xs text-red-600">{form2.formState.errors.businessName.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label>Business email *</Label>
              <Input type="email" placeholder="hello@acmecorp.com" {...form2.register("businessEmail")} />
              {form2.formState.errors.businessEmail && (
                <p className="text-xs text-red-600">{form2.formState.errors.businessEmail.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label>Business phone *</Label>
              <Input type="tel" placeholder="+1 234 567 8900" {...form2.register("businessContact")} />
              {form2.formState.errors.businessContact && (
                <p className="text-xs text-red-600">{form2.formState.errors.businessContact.message}</p>
              )}
            </div>
            <div className="space-y-1.5">
              <Label>Business logo (optional)</Label>
              <label className="flex items-center gap-3 cursor-pointer border border-dashed border-gray-300 rounded-md p-3 hover:bg-gray-50 transition-colors">
                <Upload className="h-4 w-4 text-gray-400" />
                <span className="text-sm text-gray-500">
                  {logoFile ? logoFile.name : "Click to upload logo"}
                </span>
                <input
                  type="file" accept="image/*" className="hidden"
                  onChange={(e) => setLogoFile(e.target.files?.[0] ?? null)}
                />
              </label>
            </div>
            <div className="flex gap-3">
              <Button type="button" variant="outline" className="flex-1" onClick={() => setStep(1)}>
                Back
              </Button>
              <Button type="submit" className="flex-1" disabled={form2.formState.isSubmitting}>
                {form2.formState.isSubmitting ? "Creating…" : "Create account"}
              </Button>
            </div>
          </form>
        )}

        <p className="text-center text-sm text-gray-500">
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-blue-600 hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
