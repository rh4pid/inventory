"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { Palette } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { settings as settingsApi } from "@/lib/api";
import { errorMessage } from "@/lib/utils";
import { resolveTheme } from "@/lib/themes";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { BusinessProfile } from "@/Types";

/** The company's name and contact details, plus its currency (shown on every price). */
export default function BusinessInfoPage() {
  const { activeBusiness, refreshBusinesses } = useBusiness();
  const { settings, setSettings } = useBusinessSettings();
  const [profile, setProfile] = useState<BusinessProfile | null>(null);
  const [form, setForm] = useState({ name: "", email: "", contact: "", secondaryContact: "", currencyCode: "", currencySymbol: "" });
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const bizId = activeBusiness?.businessId;

  useEffect(() => {
    if (!bizId) return;
    settingsApi.profile(bizId).then(setProfile).catch((err) => setError(errorMessage(err)));
  }, [bizId]);

  useEffect(() => {
    if (!profile || !settings) return;
    setForm({
      name: profile.name, email: profile.email ?? "", contact: profile.contact ?? "",
      secondaryContact: profile.secondaryContact ?? "",
      currencyCode: settings.currencyCode ?? "", currencySymbol: settings.currencySymbol ?? "",
    });
  }, [profile, settings]);

  if (error) return <EmptyState title="Business info" description={error} />;
  if (!profile || !settings || !bizId) return <PageLoader />;

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) return;
    setSaving(true);
    try {
      const p = await settingsApi.updateProfile(bizId, {
        name: form.name.trim(), email: form.email.trim() || null, contact: form.contact.trim() || null,
        secondaryContact: form.secondaryContact.trim() || null,
      });
      setProfile(p);
      if (form.currencyCode !== settings.currencyCode || form.currencySymbol !== settings.currencySymbol) {
        setSettings(await settingsApi.update(bizId, {
          currencyCode: form.currencyCode.trim().toUpperCase() || "GHS",
          currencySymbol: form.currencySymbol.trim() || "GH₵",
        }));
      }
      await refreshBusinesses();
      toast.success("Business info saved");
    } catch (err) {
      toast.error(errorMessage(err, "Could not save"));
    } finally {
      setSaving(false);
    }
  };

  const theme = resolveTheme(settings.theme, settings.businessType);

  return (
    <div className="max-w-3xl">
      <PageHeader title="Business info" description={`Company code ${profile.code}`} />
      <form onSubmit={save} className="space-y-5">
        <section className="space-y-4 rounded-4xl border border-line/80 bg-surface p-6 shadow-card">
          <h2 className="text-lg font-semibold text-ink">Details</h2>
          <div className="space-y-1.5">
            <Label htmlFor="bi-name">Business name *</Label>
            <Input id="bi-name" value={form.name} onChange={set("name")} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="bi-email">Email</Label>
              <Input id="bi-email" type="email" value={form.email} onChange={set("email")} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="bi-phone">Phone</Label>
              <Input id="bi-phone" value={form.contact} onChange={set("contact")} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="bi-phone2">Other phone</Label>
              <Input id="bi-phone2" value={form.secondaryContact} onChange={set("secondaryContact")} placeholder="Optional" />
            </div>
          </div>
        </section>

        <section className="space-y-4 rounded-4xl border border-line/80 bg-surface p-6 shadow-card">
          <div>
            <h2 className="text-lg font-semibold text-ink">Currency</h2>
            <p className="text-sm text-ink-3">Shown on every price, receipt and report.</p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="bi-ccode">Currency code</Label>
              <Input id="bi-ccode" value={form.currencyCode} onChange={set("currencyCode")} placeholder="e.g. GHS" maxLength={3} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="bi-csym">Symbol</Label>
              <Input id="bi-csym" value={form.currencySymbol} onChange={set("currencySymbol")} placeholder="e.g. GH₵" maxLength={6} />
            </div>
          </div>
        </section>

        <Link href="/settings/appearance"
          className="flex items-center gap-4 rounded-4xl border border-line/80 bg-surface p-5 shadow-card hover:border-brand/40">
          <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-brand-soft text-brand-deep"><Palette className="h-5 w-5" /></span>
          <span className="min-w-0 flex-1">
            <span className="block font-semibold text-ink">Appearance</span>
            <span className="block text-sm text-ink-3">Theme in use: {theme.name}. Change your company&apos;s colours.</span>
          </span>
        </Link>

        <div className="flex justify-end">
          <Button type="submit" disabled={saving || !form.name.trim()}>{saving ? "Saving…" : "Save"}</Button>
        </div>
      </form>
    </div>
  );
}
