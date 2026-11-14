"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { companies, settings as settingsApi } from "@/lib/api";
import { errorMessage } from "@/lib/utils";
import { useBusiness } from "@/context/BusinessContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { BusinessTypeOption } from "@/Types";

const empty = { name: "", businessType: "GENERAL_RETAIL", email: "", contact: "" };

/** Create a company of your own. You become its owner and the app switches to it. */
export function CreateOwnCompanyDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter();
  const { refreshBusinesses } = useBusiness();
  const [f, setF] = useState(empty);
  const [types, setTypes] = useState<BusinessTypeOption[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open && types.length === 0) settingsApi.businessTypes().then(setTypes).catch(() => {});
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  // Start fresh next time it opens.
  const close = () => { setF(empty); onClose(); };

  const set = (k: keyof typeof empty) => (e: React.ChangeEvent<HTMLInputElement>) => setF((s) => ({ ...s, [k]: e.target.value }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!f.name.trim() || busy) return;
    setBusy(true);
    try {
      const m = await companies.create({
        name: f.name.trim(),
        businessType: f.businessType,
        email: f.email.trim() || undefined,
        contact: f.contact.trim() || undefined,
      });
      await refreshBusinesses(m.businessId);
      toast.success(`${m.businessName} is ready. You're the owner.`);
      close();
      router.push("/overview");
    } catch (err) {
      toast.error(errorMessage(err, "Could not create the company"));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => !o && close()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Create a company</DialogTitle>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="oc-name">Company name *</Label>
            <Input id="oc-name" value={f.name} onChange={set("name")} placeholder="e.g. Mensah Pharmacy" autoFocus />
          </div>
          <div className="space-y-1.5">
            <Label>Business type</Label>
            <Select value={f.businessType} onValueChange={(v) => setF((s) => ({ ...s, businessType: v }))}>
              <SelectTrigger><SelectValue placeholder="Choose…" /></SelectTrigger>
              <SelectContent>
                {types.map((t) => <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>)}
              </SelectContent>
            </Select>
            <p className="text-xs text-ink-3">Switches on the right stock features (batches, expiry, pack sizes…). You can change them later.</p>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="oc-email">Company email</Label>
              <Input id="oc-email" type="email" value={f.email} onChange={set("email")} placeholder="Optional" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="oc-contact">Phone</Label>
              <Input id="oc-contact" type="tel" value={f.contact} onChange={set("contact")} placeholder="Optional" />
            </div>
          </div>
          <p className="text-xs text-ink-3">You&apos;ll be the owner. Your other companies and jobs stay as they are; switch between them any time.</p>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={close}>Cancel</Button>
            <Button type="submit" disabled={!f.name.trim() || busy}>{busy ? "Creating…" : "Create company"}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
