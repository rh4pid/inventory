"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { cn, errorMessage, formatCurrency } from "@/lib/utils";
import { accounting as accountingApi } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { AccountRef, JournalType } from "@/Types";

/** Dates as the API wants them (yyyy-mm-dd), in local time. */
export const isoDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
export const today = () => isoDate(new Date());
export const monthStart = () => { const d = new Date(); return isoDate(new Date(d.getFullYear(), d.getMonth(), 1)); };
export const yearStart = () => isoDate(new Date(new Date().getFullYear(), 0, 1));

export const JOURNAL_LABEL: Record<JournalType, string> = {
  SALE_CASH: "Sale", SALE_CREDIT: "Credit sale", PURCHASE_CASH: "Purchase", PURCHASE_CREDIT: "Credit purchase",
  CUSTOMER_PAYMENT: "Customer payment", SUPPLIER_PAYMENT: "Supplier payment", SALE_RETURN: "Sale return",
  PURCHASE_RETURN: "Purchase return", EXPENSE_CASH: "Expense", EXPENSE_CREDIT: "Expense (credit)",
  STOCK_ADJUSTMENT: "Stock adjustment", STOCK_TRANSFER: "Stock transfer", OPENING_BALANCE: "Opening stock",
  OWNER_CAPITAL: "Owner's capital", OWNER_DRAWINGS: "Owner's drawings", FUND_TRANSFER: "Money moved",
  MANUAL: "Manual entry", OTHER: "Other",
};

const TABS = [
  { href: "/accounting", label: "Overview" },
  { href: "/accounting/journals", label: "Journal" },
  { href: "/accounting/ledger", label: "Ledger" },
  { href: "/accounting/reports", label: "Reports" },
  { href: "/settings/accounts", label: "Chart of accounts" },
];

/** The row of links across the top of every accounting page. */
export function AccountingTabs() {
  const pathname = usePathname();
  return (
    <nav aria-label="Accounting" className="mb-6 flex flex-wrap gap-1 rounded-2xl bg-surface-2 p-1 print:hidden">
      {TABS.map((t) => {
        const active = t.href === "/accounting" ? pathname === t.href : pathname.startsWith(t.href);
        return (
          <Link key={t.href} href={t.href} aria-current={active ? "page" : undefined}
            className={cn("rounded-xl px-3.5 py-1.5 text-sm font-medium transition-all",
              active ? "bg-surface text-ink shadow-card" : "text-ink-3 hover:text-ink")}>
            {t.label}
          </Link>
        );
      })}
    </nav>
  );
}

/** An amount, red when negative. */
export function Amount({ value, className }: { value: number | null | undefined; className?: string }) {
  const n = Number(value ?? 0);
  return <span className={cn("tabular-nums", n < 0 && "text-red-600", className)}>{formatCurrency(n)}</span>;
}

/** Card wrapper used across accounting pages. */
export function Panel({ title, action, children, className }: {
  title?: React.ReactNode; action?: React.ReactNode; children: React.ReactNode; className?: string;
}) {
  return (
    <section className={cn("rounded-3xl border border-line/80 bg-surface p-5 shadow-card", className)}>
      {(title || action) && (
        <div className="mb-4 flex items-center justify-between gap-3">
          {title && <h2 className="text-base font-semibold text-ink">{title}</h2>}
          {action}
        </div>
      )}
      {children}
    </section>
  );
}

/** A select of accounts (value is the id as a string). */
export function AccountSelect({ accounts, value, onChange, placeholder = "Choose an account", id }: {
  accounts: { id: number; code?: string; name: string }[]; value: string; onChange: (v: string) => void;
  placeholder?: string; id?: string;
}) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger id={id}><SelectValue placeholder={placeholder} /></SelectTrigger>
      <SelectContent className="max-h-72">
        {accounts.map((a) => (
          <SelectItem key={a.id} value={String(a.id)}>{a.code ? `${a.code} · ` : ""}{a.name}</SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export type MoneyMove = "capital" | "drawings" | "transfer";

const MOVE_TEXT: Record<MoneyMove, { title: string; help: string; button: string }> = {
  capital: { title: "Owner puts money in", button: "Record capital",
    help: "Money the owner brings into the business. It goes into the account you choose and is added to Owner's Capital." },
  drawings: { title: "Owner takes money out", button: "Record drawings",
    help: "Money the owner takes for personal use. It is not a business expense, so it doesn't reduce profit." },
  transfer: { title: "Move money", button: "Move money",
    help: "Between the company's own accounts — e.g. top up petty cash from the bank, or bank the day's cash." },
};

/** Capital, drawings and transfers between money accounts. */
export function MoneyMoveDialog({ businessId, mode, open, onOpenChange, onDone, presetToPurpose }: {
  businessId: number; mode: MoneyMove; open: boolean; onOpenChange: (o: boolean) => void; onDone: () => void;
  presetToPurpose?: string;
}) {
  const [accounts, setAccounts] = useState<AccountRef[]>([]);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(today());
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    setAmount(""); setNote(""); setDate(today());
    accountingApi.moneyAccounts(businessId).then((list) => {
      setAccounts(list);
      const by = (p: string) => list.find((a) => a.purpose === p)?.id;
      if (mode === "transfer") {
        setFrom(String(by("BANK") ?? list[0]?.id ?? ""));
        setTo(String(by(presetToPurpose ?? "PETTY_CASH") ?? ""));
      } else {
        setFrom(String(by(mode === "capital" ? "BANK" : "CASH") ?? list[0]?.id ?? ""));
      }
    }).catch((err) => toast.error(errorMessage(err)));
  }, [open, businessId, mode, presetToPurpose]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const value = parseFloat(amount);
    if (!(value > 0)) { toast.error("Enter an amount above zero"); return; }
    setSaving(true);
    try {
      if (mode === "transfer") {
        await accountingApi.transfer(businessId, { fromAccountId: Number(from), toAccountId: Number(to), amount: value, date, note: note || undefined });
      } else {
        const body = { amount: value, accountId: Number(from), date, note: note || undefined };
        await (mode === "capital" ? accountingApi.capital(businessId, body) : accountingApi.drawings(businessId, body));
      }
      toast.success(mode === "transfer" ? "Money moved" : mode === "capital" ? "Capital recorded" : "Drawings recorded");
      onOpenChange(false);
      onDone();
    } catch (err) { toast.error(errorMessage(err)); }
    setSaving(false);
  };

  const t = MOVE_TEXT[mode];
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader><DialogTitle>{t.title}</DialogTitle></DialogHeader>
        <form onSubmit={submit} className="space-y-4">
          <p className="text-sm text-ink-3">{t.help}</p>
          <div className={cn("grid gap-3", mode === "transfer" && "sm:grid-cols-2")}>
            <div className="space-y-1.5">
              <Label htmlFor="mm-from">{mode === "transfer" ? "From" : mode === "capital" ? "Into" : "Out of"}</Label>
              <AccountSelect id="mm-from" accounts={accounts} value={from} onChange={setFrom} />
            </div>
            {mode === "transfer" && (
              <div className="space-y-1.5">
                <Label htmlFor="mm-to">To</Label>
                <AccountSelect id="mm-to" accounts={accounts.filter((a) => String(a.id) !== from)} value={to} onChange={setTo} />
              </div>
            )}
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="mm-amount">Amount</Label>
              <Input id="mm-amount" type="number" inputMode="decimal" min="0" step="0.01" value={amount}
                onChange={(e) => setAmount(e.target.value)} required autoFocus />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="mm-date">Date</Label>
              <Input id="mm-date" type="date" value={date} max={today()} onChange={(e) => setDate(e.target.value)} />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="mm-note">Note</Label>
            <Input id="mm-note" value={note} onChange={(e) => setNote(e.target.value)}
              placeholder={mode === "transfer" ? "e.g. Petty cash top-up" : "Optional"} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
            <Button type="submit" disabled={saving || !from || (mode === "transfer" && !to)}>{saving ? "Saving…" : t.button}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
