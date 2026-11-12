"use client";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { admin as adminApi, settings as settingsApi } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { CredentialsCard } from "@/components/account/CredentialsCard";
import type { BusinessTypeOption, CompanyResult } from "@/Types";

const empty = { name: "", email: "", contact: "", businessType: "GENERAL_RETAIL", firstName: "", lastName: "", adminEmail: "", phone: "" };

/** New company + its first admin. Ends by showing the admin's temporary password to hand over. */
export function CreateCompanyDialog({ open, onClose, onCreated }: {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [f, setF] = useState(empty);
  const [types, setTypes] = useState<BusinessTypeOption[]>([]);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<CompanyResult | null>(null);

  useEffect(() => {
    if (open && types.length === 0) settingsApi.businessTypes().then(setTypes).catch(() => {});
    if (open) { setF(empty); setResult(null); }
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  const set = (k: keyof typeof empty) => (e: React.ChangeEvent<HTMLInputElement>) => setF((s) => ({ ...s, [k]: e.target.value }));
  const valid = f.name.trim() && f.firstName.trim() && f.lastName.trim() && f.adminEmail.trim();

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!valid) return;
    setBusy(true);
    try {
      const r = await adminApi.createCompany({
        name: f.name.trim(),
        email: f.email.trim() || undefined,
        contact: f.contact.trim() || undefined,
        businessType: f.businessType,
        admin: { firstName: f.firstName.trim(), lastName: f.lastName.trim(), email: f.adminEmail.trim(), phone: f.phone.trim() || undefined },
      });
      setResult(r);
      onCreated();
      toast.success(`${r.company.name} created`);
    } catch (err: any) {
      toast.error(err.message ?? "Could not create the company");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>{result ? `${result.company.name} is ready` : "New company"}</DialogTitle>
        </DialogHeader>

        {result ? (
          <div className="space-y-4">
            <p className="text-sm text-ink-2">
              Company code <b className="font-mono">{result.company.code}</b> · {result.company.businessTypeLabel}.
              Hand these details to the company admin. They&apos;ll add their branches, team and products.
            </p>
            <CredentialsCard {...result.credentials} title="Company admin sign-in" />
            <DialogFooter><Button onClick={onClose}>Done</Button></DialogFooter>
          </div>
        ) : (
          <form onSubmit={submit} className="space-y-5">
            <fieldset className="space-y-3">
              <legend className="mb-1 text-xs font-semibold uppercase tracking-wide text-ink-3">Company</legend>
              <div className="space-y-1.5">
                <Label htmlFor="cc-name">Company name *</Label>
                <Input id="cc-name" value={f.name} onChange={set("name")} placeholder="e.g. Mensah Pharmacy Ltd" />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="cc-email">Company email</Label>
                  <Input id="cc-email" type="email" value={f.email} onChange={set("email")} placeholder="Optional" />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="cc-contact">Phone</Label>
                  <Input id="cc-contact" value={f.contact} onChange={set("contact")} placeholder="Optional" />
                </div>
              </div>
              <div className="space-y-1.5">
                <Label>Business type</Label>
                <Select value={f.businessType} onValueChange={(v) => setF((s) => ({ ...s, businessType: v }))}>
                  <SelectTrigger><SelectValue placeholder="Choose…" /></SelectTrigger>
                  <SelectContent>
                    {types.map((t) => <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>)}
                  </SelectContent>
                </Select>
                <p className="text-xs text-ink-3">Switches on the right stock features (batches, expiry, pack sizes…). The admin can change them later.</p>
              </div>
            </fieldset>

            <fieldset className="space-y-3">
              <legend className="mb-1 text-xs font-semibold uppercase tracking-wide text-ink-3">Company admin</legend>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="cc-first">First name *</Label>
                  <Input id="cc-first" value={f.firstName} onChange={set("firstName")} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="cc-last">Last name *</Label>
                  <Input id="cc-last" value={f.lastName} onChange={set("lastName")} />
                </div>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="cc-aemail">Email (sign-in) *</Label>
                  <Input id="cc-aemail" type="email" value={f.adminEmail} onChange={set("adminEmail")} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="cc-phone">Phone</Label>
                  <Input id="cc-phone" value={f.phone} onChange={set("phone")} placeholder="Optional" />
                </div>
              </div>
            </fieldset>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
              <Button type="submit" variant="accent" disabled={!valid || busy}>{busy ? "Creating…" : "Create company"}</Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
