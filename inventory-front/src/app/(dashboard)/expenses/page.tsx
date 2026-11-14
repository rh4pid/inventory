"use client";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Plus, Check, X, Ban, Undo2, ArrowLeftRight } from "lucide-react";
import { useBusiness } from "@/context/BusinessContext";
import { useCan } from "@/lib/access";
import { expenses as expensesApi } from "@/lib/api";
import { errorMessage, formatCurrency, formatDate } from "@/lib/utils";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AccountSelect, MoneyMoveDialog, today } from "@/components/accounting/shared";
import type { Expense, ExpenseOptions, ExpenseStatus, PageResponse } from "@/Types";

const SIZE = 25;
const STATUS_BADGE: Record<ExpenseStatus, { label: string; variant: "warning" | "success" | "destructive" | "secondary" }> = {
  PENDING: { label: "Waiting for approval", variant: "warning" },
  APPROVED: { label: "Approved", variant: "success" },
  REJECTED: { label: "Rejected", variant: "destructive" },
  CANCELLED: { label: "Cancelled", variant: "secondary" },
  VOID: { label: "Voided", variant: "secondary" },
};

type Filter = ExpenseStatus | "ALL";
type NoteAction = { kind: "reject" | "void"; expense: Expense };

export default function ExpensesPage() {
  const { activeBusiness } = useBusiness();
  const businessId = activeBusiness?.businessId;
  const can = useCan();
  const [options, setOptions] = useState<ExpenseOptions | null>(null);
  const [data, setData] = useState<PageResponse<Expense> | null>(null);
  const [filter, setFilter] = useState<Filter | null>(null);
  const [page, setPage] = useState(0);
  const [recording, setRecording] = useState(false);
  const [topUp, setTopUp] = useState(false);
  const [noteAction, setNoteAction] = useState<NoteAction | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const loadOptions = useCallback(async () => {
    if (!businessId || !can("RECORD_EXPENSES")) return;
    try { setOptions(await expensesApi.options(businessId)); } catch (err) { toast.error(errorMessage(err)); }
  }, [businessId, can]);

  // Approvers land on what's waiting for them; everyone else on their own list.
  useEffect(() => {
    if (!businessId || filter) return;
    if (!can("APPROVE")) { setFilter("ALL"); return; }
    expensesApi.pendingCount(businessId).then((r) => setFilter(r.count > 0 ? "PENDING" : "ALL")).catch(() => setFilter("ALL"));
  }, [businessId, can, filter]);

  const load = useCallback(async () => {
    if (!businessId || !filter) return;
    try {
      setData(await expensesApi.list(businessId, { status: filter === "ALL" ? undefined : filter, page, size: SIZE }));
    } catch (err) { toast.error(errorMessage(err)); }
  }, [businessId, filter, page]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { loadOptions(); }, [loadOptions]);

  const refresh = () => { load(); loadOptions(); };

  const act = async (e: Expense, fn: () => Promise<Expense>, done: string) => {
    setBusyId(e.id);
    try { await fn(); toast.success(done); refresh(); } catch (err) { toast.error(errorMessage(err)); }
    setBusyId(null);
  };

  if (!businessId || !data || !filter) return <PageLoader />;

  const pettyCash = options?.paidFrom.find((o) => o.id === options.defaultPaidFromId);

  return (
    <div>
      <PageHeader title="Expenses"
        description={options?.needsApproval
          ? "Money spent from petty cash for the branch. A manager approves each one."
          : "Running costs paid from petty cash or another account. Approved expenses go straight to the books."}
        action={can("RECORD_EXPENSES") && <Button onClick={() => setRecording(true)}><Plus className="h-4 w-4" /> Record expense</Button>} />

      {pettyCash && pettyCash.balance != null && (
        <div className="mb-4 flex flex-wrap items-center gap-3 rounded-2xl border border-line/80 bg-surface px-4 py-3 shadow-card">
          <span className="text-sm text-ink-3">Petty cash balance</span>
          <span className="text-lg font-semibold tabular-nums text-ink">{formatCurrency(pettyCash.balance)}</span>
          {can("MANAGE_ACCOUNTS") && (
            <Button size="sm" variant="outline" className="ml-auto" onClick={() => setTopUp(true)}>
              <ArrowLeftRight className="h-4 w-4" /> Top up petty cash
            </Button>
          )}
        </div>
      )}

      <Tabs value={filter} onValueChange={(v) => { setFilter(v as Filter); setPage(0); }} className="mb-4">
        <TabsList>
          <TabsTrigger value="PENDING">Waiting</TabsTrigger>
          <TabsTrigger value="APPROVED">Approved</TabsTrigger>
          <TabsTrigger value="REJECTED">Rejected</TabsTrigger>
          <TabsTrigger value="ALL">All</TabsTrigger>
        </TabsList>
      </Tabs>

      {data.content.length === 0 ? (
        <EmptyState title={filter === "PENDING" ? "Nothing waiting" : "No expenses"}
          description={filter === "PENDING" ? "No expenses need approval right now." : "Expenses you record will appear here."} />
      ) : (
        <div className="overflow-hidden rounded-3xl border border-line/80 bg-surface shadow-card">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>For</TableHead>
                  <TableHead>Details</TableHead>
                  <TableHead>Branch</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
                  <TableHead>Paid from</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-44"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((e) => (
                  <TableRow key={e.id}>
                    <TableCell className="whitespace-nowrap text-sm">
                      {formatDate(e.date)}
                      <div className="font-mono text-xs text-ink-3">{e.reference}</div>
                    </TableCell>
                    <TableCell className="text-sm font-medium">{e.expenseAccountName}</TableCell>
                    <TableCell className="max-w-xs text-sm">
                      {e.description}
                      <div className="text-xs text-ink-3">
                        {[e.payee, e.receiptNumber && `Receipt ${e.receiptNumber}`, `by ${e.requestedBy}`].filter(Boolean).join(" · ")}
                      </div>
                      {e.decisionNote && <div className="text-xs italic text-ink-3">“{e.decisionNote}” — {e.decidedBy}</div>}
                    </TableCell>
                    <TableCell className="text-sm text-ink-3">{e.shopName ?? "Whole company"}</TableCell>
                    <TableCell className="text-right text-sm font-medium tabular-nums">{formatCurrency(e.amount)}</TableCell>
                    <TableCell className="text-sm text-ink-3">{e.paidFromName}</TableCell>
                    <TableCell><Badge variant={STATUS_BADGE[e.status].variant}>{STATUS_BADGE[e.status].label}</Badge></TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        {e.canApprove && (
                          <>
                            <Button size="sm" disabled={busyId === e.id}
                              onClick={() => act(e, () => expensesApi.approve(businessId, e.id), "Expense approved")}>
                              <Check className="h-4 w-4" /> Approve
                            </Button>
                            <Button size="icon" variant="ghost" aria-label="Reject" disabled={busyId === e.id}
                              onClick={() => setNoteAction({ kind: "reject", expense: e })}><X className="h-4 w-4" /></Button>
                          </>
                        )}
                        {!e.canApprove && e.canCancel && (
                          <Button size="sm" variant="ghost" disabled={busyId === e.id}
                            onClick={() => act(e, () => expensesApi.cancel(businessId, e.id), "Expense cancelled")}>
                            <Ban className="h-4 w-4" /> Cancel
                          </Button>
                        )}
                        {e.canVoid && (
                          <Button size="sm" variant="ghost" disabled={busyId === e.id}
                            onClick={() => setNoteAction({ kind: "void", expense: e })}>
                            <Undo2 className="h-4 w-4" /> Void
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          {data.totalElements > SIZE && (
            <div className="flex items-center justify-between border-t border-line px-4 py-3">
              <p className="text-xs text-ink-3">{page * SIZE + 1}–{Math.min((page + 1) * SIZE, data.totalElements)} of {data.totalElements}</p>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
                <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>Next</Button>
              </div>
            </div>
          )}
        </div>
      )}

      {options && (
        <RecordExpenseDialog businessId={businessId} options={options} open={recording} onOpenChange={setRecording}
          onDone={(e) => { if (e.status === "PENDING" && filter !== "PENDING" && filter !== "ALL") setFilter("ALL"); refresh(); }} />
      )}
      {topUp && (
        <MoneyMoveDialog businessId={businessId} mode="transfer" presetToPurpose="PETTY_CASH" open
          onOpenChange={(o) => !o && setTopUp(false)} onDone={refresh} />
      )}
      <NoteDialog action={noteAction} onClose={() => setNoteAction(null)}
        onSubmit={(note) => {
          if (!noteAction) return;
          const { kind, expense } = noteAction;
          setNoteAction(null);
          act(expense, () => kind === "reject" ? expensesApi.reject(businessId, expense.id, note) : expensesApi.void(businessId, expense.id, note),
            kind === "reject" ? "Expense rejected" : "Expense voided and reversed in the books");
        }} />
    </div>
  );
}

function RecordExpenseDialog({ businessId, options, open, onOpenChange, onDone }: {
  businessId: number; options: ExpenseOptions; open: boolean; onOpenChange: (o: boolean) => void; onDone: (e: Expense) => void;
}) {
  const [accountId, setAccountId] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(today());
  const [shopId, setShopId] = useState("");
  const [paidFrom, setPaidFrom] = useState("");
  const [description, setDescription] = useState("");
  const [payee, setPayee] = useState("");
  const [receipt, setReceipt] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setAccountId(""); setAmount(""); setDate(today()); setDescription(""); setPayee(""); setReceipt("");
    setShopId(options.branches.length === 1 ? String(options.branches[0].id) : options.needsApproval ? "" : "COMPANY");
    setPaidFrom(String(options.defaultPaidFromId ?? options.paidFrom[0]?.id ?? ""));
  }, [open, options]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const value = parseFloat(amount);
    if (!accountId) { toast.error("Choose what the money was for"); return; }
    if (!(value > 0)) { toast.error("Enter an amount above zero"); return; }
    setSaving(true);
    try {
      const saved = await expensesApi.create(businessId, {
        expenseAccountId: Number(accountId), amount: value, date, description,
        shopId: shopId && shopId !== "COMPANY" ? Number(shopId) : undefined,
        paidFromAccountId: paidFrom ? Number(paidFrom) : undefined,
        payee: payee || undefined, receiptNumber: receipt || undefined,
      });
      toast.success(saved.status === "PENDING" ? "Sent to a manager for approval" : "Expense recorded");
      onOpenChange(false);
      onDone(saved);
    } catch (err) { toast.error(errorMessage(err)); }
    setSaving(false);
  };

  const from = options.paidFrom.find((o) => String(o.id) === paidFrom);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader><DialogTitle>Record expense</DialogTitle></DialogHeader>
        <form onSubmit={submit} className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="ex-acc">What was it for?</Label>
              <AccountSelect id="ex-acc" accounts={options.expenseAccounts.map((o) => ({ id: o.id, name: o.name }))}
                value={accountId} onChange={setAccountId} placeholder="e.g. Electricity" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ex-amount">Amount</Label>
              <Input id="ex-amount" type="number" inputMode="decimal" min="0" step="0.01" value={amount}
                onChange={(e) => setAmount(e.target.value)} required />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ex-desc">Description</Label>
            <Input id="ex-desc" value={description} onChange={(e) => setDescription(e.target.value)} required
              placeholder="e.g. Prepaid electricity units for the shop" />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="ex-payee">Paid to</Label>
              <Input id="ex-payee" value={payee} onChange={(e) => setPayee(e.target.value)} placeholder="e.g. ECG" />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ex-receipt">Receipt / bill no.</Label>
              <Input id="ex-receipt" value={receipt} onChange={(e) => setReceipt(e.target.value)} placeholder="Optional" />
            </div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="ex-shop">Branch</Label>
              <Select value={shopId} onValueChange={setShopId}>
                <SelectTrigger id="ex-shop"><SelectValue placeholder="Choose a branch" /></SelectTrigger>
                <SelectContent>
                  {!options.needsApproval && <SelectItem value="COMPANY">Whole company</SelectItem>}
                  {options.branches.map((b) => <SelectItem key={b.id} value={String(b.id)}>{b.name}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ex-date">Date</Label>
              <Input id="ex-date" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
            </div>
          </div>
          {options.paidFrom.length > 1 && (
            <div className="space-y-1.5">
              <Label htmlFor="ex-from">Paid from</Label>
              <AccountSelect id="ex-from" accounts={options.paidFrom.map((o) => ({
                id: o.id, name: o.balance != null ? `${o.name} (${formatCurrency(o.balance)})` : o.name,
              }))} value={paidFrom} onChange={setPaidFrom} />
            </div>
          )}
          {options.paidFrom.length === 1 && <p className="text-sm text-ink-3">Paid from {options.paidFrom[0].name}.</p>}
          {from?.balance != null && Number(amount) > Number(from.balance) && (
            <p className="rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900">
              {from.name} only has {formatCurrency(from.balance)}. It needs topping up before this can be paid.
            </p>
          )}
          {options.needsApproval && (
            <p className="rounded-xl bg-surface-2 px-3 py-2 text-sm text-ink-2">A manager will approve this before it goes into the books.</p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
            <Button type="submit" disabled={saving || !description.trim() || (options.needsApproval && !shopId)}>
              {saving ? "Saving…" : options.needsApproval ? "Send for approval" : "Record expense"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function NoteDialog({ action, onClose, onSubmit }: {
  action: NoteAction | null; onClose: () => void; onSubmit: (note: string | undefined) => void;
}) {
  const [note, setNote] = useState("");
  useEffect(() => { if (action) setNote(""); }, [action]);
  const isVoid = action?.kind === "void";
  return (
    <Dialog open={!!action} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-md">
        <DialogHeader><DialogTitle>{isVoid ? "Void this expense?" : "Reject this expense?"}</DialogTitle></DialogHeader>
        {action && (
          <p className="text-sm text-ink-3">
            {action.expense.expenseAccountName} · {formatCurrency(action.expense.amount)} · {action.expense.description}
            {isVoid && <><br />The entry is reversed in the books and the money goes back to {action.expense.paidFromName}.</>}
          </p>
        )}
        <div className="space-y-1.5">
          <Label htmlFor="note">Reason</Label>
          <Input id="note" value={note} onChange={(e) => setNote(e.target.value)} autoFocus
            placeholder={isVoid ? "e.g. Recorded twice" : "e.g. Pay this from the bank instead"} />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Back</Button>
          <Button variant="destructive" onClick={() => onSubmit(note || undefined)}>{isVoid ? "Void expense" : "Reject"}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
