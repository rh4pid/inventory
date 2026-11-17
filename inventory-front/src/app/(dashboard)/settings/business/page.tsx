"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ImagePlus, Palette, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { settings as settingsApi, businessLogoUrl } from "@/lib/api";
import { shrinkImage } from "@/lib/images";
import { LogoMark } from "@/components/layout/Logo";
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
  const [logoBusy, setLogoBusy] = useState(false);
  const fileInput = useRef<HTMLInputElement | null>(null);
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

  const theme = resolveTheme(settings.theme);
  const logoSrc = businessLogoUrl(profile.logo);

  const pickLogo = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    setLogoBusy(true);
    try {
      const p = await settingsApi.uploadLogo(bizId, await shrinkImage(file, 512, 0.9));
      setProfile(p);
      await refreshBusinesses();
      toast.success("Logo updated");
    } catch (err) {
      toast.error(errorMessage(err, "Could not upload the logo"));
    } finally {
      setLogoBusy(false);
    }
  };

  const removeLogo = async () => {
    setLogoBusy(true);
    try {
      setProfile(await settingsApi.removeLogo(bizId));
      await refreshBusinesses();
      toast.success("Logo removed");
    } catch (err) {
      toast.error(errorMessage(err, "Could not remove the logo"));
    } finally {
      setLogoBusy(false);
    }
  };

  return (
    <div className="max-w-3xl">
      <PageHeader title="Business info" description={`Company code ${profile.code}`} />
      <form onSubmit={save} className="space-y-5">
        <section className="flex flex-col gap-4 rounded-4xl border border-line/80 bg-surface p-6 shadow-card sm:flex-row sm:items-center">
          <div className="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-2xl border border-line/80 bg-white">
            {logoSrc
              // eslint-disable-next-line @next/next/no-img-element
              ? <img src={logoSrc} alt={`${profile.name} logo`} className="h-full w-full object-contain" />
              : <LogoMark className="h-10 w-10" />}
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="text-lg font-semibold text-ink">Logo</h2>
            <p className="text-sm text-ink-3">Shown in the menu and on invitations. A square JPG, PNG or WebP works best.</p>
          </div>
          <div className="flex gap-2">
            <input ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp" className="hidden" onChange={pickLogo} />
            <Button type="button" variant="outline" disabled={logoBusy} onClick={() => fileInput.current?.click()}>
              <ImagePlus className="mr-1.5 h-4 w-4" /> {logoBusy ? "Uploading…" : logoSrc ? "Change" : "Upload"}
            </Button>
            {logoSrc && (
              <Button type="button" variant="ghost" disabled={logoBusy} onClick={removeLogo} aria-label="Remove logo">
                <Trash2 className="h-4 w-4" />
              </Button>
            )}
          </div>
        </section>

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
