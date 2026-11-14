"use client";
import Link from "next/link";
import { errorMessage } from "@/lib/utils";
import { useEffect, useState, useCallback } from "react";
import { Plus, Pencil, RefreshCw, Lock } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { accounts as accountsApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AccountingTabs } from "@/components/accounting/shared";
import type { Account, AccountType } from "@/Types";

const TYPES: { value: AccountType; label: string; variant: "default" | "success" | "destructive" | "warning" | "secondary" }[] = [
  { value: "ASSET", label: "Asset", variant: "success" },
  { value: "LIABILITY", label: "Liability", variant: "destructive" },
  { value: "EQUITY", label: "Equity", variant: "default" },
  { value: "REVENUE", label: "Income", variant: "success" },
  { value: "EXPENSE", label: "Expense", variant: "warning" },
];
const typeOf = (t: AccountType) => TYPES.find((x) => x.value === t) ?? TYPES[0];
/** Accounts the system posts to by purpose: renamable, but not removable or retypeable. */
const isSystem = (a: Account) => !!a.accountPurpose && a.accountPurpose !== "EXPENSE" && a.accountPurpose !== "OTHER";

export default function AccountsPage() {
  const { activeBusiness } = useBusiness();
  const businessId = activeBusiness?.businessId;
  const [items, setItems] = useState<Account[] | null>(null);
  const [setting, setSetting] = useState(false);
  const [editing, setEditing] = useState<Account | "new" | null>(null);

  const load = useCallback(async () => {
    if (!businessId) return;
    try { setItems(await accountsApi.list(businessId)); } catch (err) { toast.error(errorMessage(err)); }
  }, [businessId]);

  useEffect(() => { load(); }, [load]);

  const setupDefaults = async () => {
    if (!businessId) return;
    setSetting(true);
    try {
      const before = items?.length ?? 0;
      const list = await accountsApi.setupDefaults(businessId);
      setItems(list);
      toast.success(list.length > before ? `Added ${list.length - before} standard accounts` : "All standard accounts are already set up");
    } catch (err) { toast.error(errorMessage(err, "Setup failed")); }
    setSetting(false);
  };

  if (!items || !businessId) return <PageLoader />;

  return (
    <div>
      <PageHeader title="Chart of accounts" description="Every account the books use. Sales, purchases, expenses and stock post to these automatically."
        action={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={setupDefaults} disabled={setting}>
              <RefreshCw className={`h-4 w-4 ${setting ? "animate-spin" : ""}`} /> {setting ? "Checking…" : "Add missing standard accounts"}
            </Button>
            <Button onClick={() => setEditing("new")}><Plus className="h-4 w-4" /> New account</Button>
          </div>
        } />
      <AccountingTabs />

      <div className="overflow-hidden rounded-3xl border border-line/80 bg-surface shadow-card">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-24">Code</TableHead>
                <TableHead>Account</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-28"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((a) => (
                <TableRow key={a.id} className={a.active ? "" : "opacity-60"}>
                  <TableCell className="font-mono text-sm text-ink-3">{a.accountCode}</TableCell>
                  <TableCell className="text-sm font-medium">
                    <Link href={`/accounting/ledger?account=${a.id}`} className="hover:underline">{a.accountName}</Link>
                    {isSystem(a) && <span title="Used by the system" className="ml-2 inline-flex text-ink-3"><Lock className="h-3 w-3" /></span>}
                  </TableCell>
                  <TableCell><Badge variant={typeOf(a.accountType).variant}>{typeOf(a.accountType).label}</Badge></TableCell>
                  <TableCell><Badge variant={a.active ? "success" : "secondary"}>{a.active ? "Active" : "Off"}</Badge></TableCell>
                  <TableCell className="text-right">
                    <Button size="sm" variant="ghost" onClick={() => setEditing(a)}><Pencil className="h-3.5 w-3.5" /> Edit</Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>

      <AccountDialog businessId={businessId} account={editing} onClose={() => setEditing(null)} onSaved={load} />
    </div>
  );
}

function AccountDialog({ businessId, account, onClose, onSaved }: {
  businessId: number; account: Account | "new" | null; onClose: () => void; onSaved: () => void;
}) {
  const existing = account && account !== "new" ? account : null;
  const system = existing ? isSystem(existing) : false;
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [type, setType] = useState<AccountType>("EXPENSE");
  const [active, setActive] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!account) return;
    setCode(existing?.accountCode ?? "");
    setName(existing?.accountName ?? "");
    setType(existing?.accountType ?? "EXPENSE");
    setActive(existing?.active ?? true);
  }, [account]); // eslint-disable-line react-hooks/exhaustive-deps

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const body = { accountCode: code.trim(), accountName: name.trim(), accountType: type,
        accountPurpose: existing?.accountPurpose, active };
      if (existing) await accountsApi.update(businessId, existing.id, body);
      else await accountsApi.create(businessId, body);
      toast.success(existing ? "Account updated" : "Account added");
      onClose();
      onSaved();
    } catch (err) { toast.error(errorMessage(err)); }
    setSaving(false);
  };

  return (
    <Dialog open={!!account} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader><DialogTitle>{existing ? "Edit account" : "New account"}</DialogTitle></DialogHeader>
        <form onSubmit={submit} className="space-y-4">
          {system && <p className="rounded-xl bg-surface-2 px-3 py-2 text-sm text-ink-2">The system posts to this account, so only its name and code can change.</p>}
          {!existing && <p className="text-sm text-ink-3">New expense accounts (e.g. &ldquo;Generator fuel&rdquo;) appear in the expense form straight away.</p>}
          <div className="grid gap-3 sm:grid-cols-[7rem_1fr]">
            <div className="space-y-1.5">
              <Label htmlFor="ac-code">Code</Label>
              <Input id="ac-code" value={code} onChange={(e) => setCode(e.target.value)} required placeholder="6120" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ac-name">Name</Label>
              <Input id="ac-name" value={name} onChange={(e) => setName(e.target.value)} required placeholder="Generator fuel" />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ac-type">Type</Label>
            <Select value={type} onValueChange={(v) => setType(v as AccountType)} disabled={system}>
              <SelectTrigger id="ac-type"><SelectValue /></SelectTrigger>
              <SelectContent>{TYPES.map((t) => <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>)}</SelectContent>
            </Select>
          </div>
          {existing && !system && (
            <label className="flex items-center justify-between rounded-xl border border-line px-3 py-2.5 text-sm">
              <span>Active <span className="block text-xs text-ink-3">Switched-off accounts keep their history but can&apos;t be used.</span></span>
              <Switch checked={active} onCheckedChange={setActive} />
            </label>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Cancel</Button>
            <Button type="submit" disabled={saving}>{saving ? "Saving…" : existing ? "Save" : "Add account"}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
