"use client";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Plus, Trash2 } from "lucide-react";
import { useBusiness } from "@/context/BusinessContext";
import { accounting as accountingApi, accounts as accountsApi } from "@/lib/api";
import { cn, errorMessage, formatCurrency } from "@/lib/utils";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { AccountingTabs, AccountSelect, JOURNAL_LABEL, monthStart, today } from "@/components/accounting/shared";
import type { Account, JournalType, JournalView, PageResponse } from "@/Types";

const SIZE = 20;

export default function JournalsPage() {
  const { activeBusiness } = useBusiness();
  const businessId = activeBusiness?.businessId;
  const [data, setData] = useState<PageResponse<JournalView> | null>(null);
  const [page, setPage] = useState(0);
  const [type, setType] = useState<JournalType | "ALL">("ALL");
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(today());
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    if (!businessId) return;
    try {
      setData(await accountingApi.journals(businessId, {
        type: type === "ALL" ? undefined : type, from: from || undefined, to: to || undefined, page, size: SIZE,
      }));
    } catch (err) { toast.error(errorMessage(err)); }
  }, [businessId, type, from, to, page]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { if (new URLSearchParams(window.location.search).get("new")) setAdding(true); }, []);

  if (!data || !businessId) return <PageLoader />;

  return (
    <div>
      <PageHeader title="Journal" description="Every entry in the books, newest first"
        action={<Button onClick={() => setAdding(true)}><Plus className="h-4 w-4" /> Manual entry</Button>} />
      <AccountingTabs />

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="w-48 space-y-1.5">
          <Label htmlFor="j-type">Type</Label>
          <Select value={type} onValueChange={(v) => { setType(v as JournalType | "ALL"); setPage(0); }}>
            <SelectTrigger id="j-type"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All entries</SelectItem>
              {(Object.keys(JOURNAL_LABEL) as JournalType[]).map((t) => <SelectItem key={t} value={t}>{JOURNAL_LABEL[t]}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="j-from">From</Label>
          <Input id="j-from" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="j-to">To</Label>
          <Input id="j-to" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} />
        </div>
      </div>

      {data.content.length === 0 ? (
        <EmptyState title="No entries" description="Nothing was posted in this period." />
      ) : (
        <div className="space-y-3">
          {data.content.map((j) => (
            <article key={j.id} className="rounded-3xl border border-line/80 bg-surface p-4 shadow-card">
              <header className="mb-3 flex flex-wrap items-start justify-between gap-2">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-xs text-ink-3">{j.reference}</span>
                    <Badge variant="secondary">{JOURNAL_LABEL[j.type] ?? j.type}</Badge>
                    {j.shopName && <span className="text-xs text-ink-3">{j.shopName}</span>}
                  </div>
                  <p className="mt-1 text-sm font-medium text-ink">{j.description}</p>
                </div>
                <div className="text-right text-xs text-ink-3">
                  <div>{new Date(j.date).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}</div>
                  {j.createdBy && <div>{j.createdBy}</div>}
                </div>
              </header>
              <table className="w-full text-sm">
                <thead className="text-xs text-ink-3">
                  <tr><th className="pb-1 text-left font-medium">Account</th><th className="w-32 pb-1 text-right font-medium">Debit</th><th className="w-32 pb-1 text-right font-medium">Credit</th></tr>
                </thead>
                <tbody>
                  {j.lines.map((l, i) => (
                    <tr key={i} className="border-t border-line/60">
                      <td className={cn("py-1.5", Number(l.credit) > 0 && "pl-6")}>
                        <span className="font-mono text-xs text-ink-3">{l.accountCode}</span> {l.accountName}
                      </td>
                      <td className="py-1.5 text-right tabular-nums">{Number(l.debit) > 0 ? formatCurrency(l.debit) : ""}</td>
                      <td className="py-1.5 text-right tabular-nums">{Number(l.credit) > 0 ? formatCurrency(l.credit) : ""}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </article>
          ))}
          {data.totalElements > SIZE && (
            <div className="flex items-center justify-between pt-2">
              <p className="text-xs text-ink-3">{page * SIZE + 1}–{Math.min((page + 1) * SIZE, data.totalElements)} of {data.totalElements}</p>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
                <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>Next</Button>
              </div>
            </div>
          )}
        </div>
      )}

      <ManualEntryDialog businessId={businessId} open={adding} onOpenChange={setAdding} onDone={() => { setPage(0); load(); }} />
    </div>
  );
}

type Line = { accountId: string; debit: string; credit: string };
const blankLine = (): Line => ({ accountId: "", debit: "", credit: "" });

function ManualEntryDialog({ businessId, open, onOpenChange, onDone }: {
  businessId: number; open: boolean; onOpenChange: (o: boolean) => void; onDone: () => void;
}) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [description, setDescription] = useState("");
  const [date, setDate] = useState(today());
  const [lines, setLines] = useState<Line[]>([blankLine(), blankLine()]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setDescription(""); setDate(today()); setLines([blankLine(), blankLine()]);
    accountsApi.list(businessId).then((l) => setAccounts(l.filter((a) => a.active))).catch((e) => toast.error(errorMessage(e)));
  }, [open, businessId]);

  const num = (s: string) => (s ? parseFloat(s) || 0 : 0);
  const dr = lines.reduce((s, l) => s + num(l.debit), 0);
  const cr = lines.reduce((s, l) => s + num(l.credit), 0);
  const balanced = Math.abs(dr - cr) < 0.005 && dr > 0;
  const set = (i: number, patch: Partial<Line>) => setLines((ls) => ls.map((l, k) => (k === i ? { ...l, ...patch } : l)));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await accountingApi.manualJournal(businessId, {
        description, date,
        lines: lines.filter((l) => l.accountId && (num(l.debit) || num(l.credit)))
          .map((l) => ({ accountId: Number(l.accountId), debit: num(l.debit) || undefined, credit: num(l.credit) || undefined })),
      });
      toast.success("Entry posted");
      onOpenChange(false);
      onDone();
    } catch (err) { toast.error(errorMessage(err)); }
    setSaving(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader><DialogTitle>Manual entry</DialogTitle></DialogHeader>
        <form onSubmit={submit} className="space-y-4">
          <p className="text-sm text-ink-3">For opening balances, corrections and anything the system doesn&apos;t post itself. Debits must equal credits.</p>
          <div className="grid gap-3 sm:grid-cols-[1fr_10rem]">
            <div className="space-y-1.5">
              <Label htmlFor="me-desc">Description</Label>
              <Input id="me-desc" value={description} onChange={(e) => setDescription(e.target.value)} required
                placeholder="e.g. Opening bank balance" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="me-date">Date</Label>
              <Input id="me-date" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
            </div>
          </div>
          <div className="space-y-2">
            <div className="hidden grid-cols-[1fr_8rem_8rem_2.5rem] gap-2 text-xs font-medium text-ink-3 sm:grid">
              <span>Account</span><span className="text-right">Debit</span><span className="text-right">Credit</span><span />
            </div>
            {lines.map((l, i) => (
              <div key={i} className="grid grid-cols-2 gap-2 sm:grid-cols-[1fr_8rem_8rem_2.5rem]">
                <div className="col-span-2 sm:col-span-1">
                  <AccountSelect accounts={accounts.map((a) => ({ id: a.id, code: a.accountCode, name: a.accountName }))}
                    value={l.accountId} onChange={(v) => set(i, { accountId: v })} />
                </div>
                <Input aria-label="Debit" type="number" min="0" step="0.01" className="text-right" value={l.debit}
                  onChange={(e) => set(i, { debit: e.target.value, credit: e.target.value ? "" : l.credit })} />
                <Input aria-label="Credit" type="number" min="0" step="0.01" className="text-right" value={l.credit}
                  onChange={(e) => set(i, { credit: e.target.value, debit: e.target.value ? "" : l.debit })} />
                <Button type="button" variant="ghost" size="icon" aria-label="Remove line" disabled={lines.length <= 2}
                  onClick={() => setLines((ls) => ls.filter((_, k) => k !== i))}><Trash2 className="h-4 w-4" /></Button>
              </div>
            ))}
            <Button type="button" variant="outline" size="sm" onClick={() => setLines((ls) => [...ls, blankLine()])}>
              <Plus className="h-4 w-4" /> Add line
            </Button>
          </div>
          <div className={cn("flex justify-between rounded-xl px-3 py-2 text-sm", balanced ? "bg-green-50 text-green-800" : "bg-surface-2 text-ink-2")}>
            <span>Debits {formatCurrency(dr)} · Credits {formatCurrency(cr)}</span>
            <span>{balanced ? "Balanced" : dr || cr ? `Off by ${formatCurrency(Math.abs(dr - cr))}` : ""}</span>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
            <Button type="submit" disabled={saving || !balanced || !description.trim()}>{saving ? "Posting…" : "Post entry"}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
