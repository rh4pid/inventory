"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { ArrowDownToLine, ArrowUpFromLine, ArrowLeftRight, BookPlus, AlertTriangle, Receipt } from "lucide-react";
import { useBusiness } from "@/context/BusinessContext";
import { accounting as accountingApi } from "@/lib/api";
import { errorMessage, formatCurrency } from "@/lib/utils";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { AccountingTabs, Amount, MoneyMoveDialog, Panel, type MoneyMove } from "@/components/accounting/shared";
import type { AccountingSummary } from "@/Types";

const PURPOSE_HINT: Record<string, string> = {
  CASH: "The tills / cash on hand", BANK: "Bank account", MOBILE_MONEY: "MoMo wallet", PETTY_CASH: "For small branch expenses",
};

export default function AccountingPage() {
  const { activeBusiness } = useBusiness();
  const businessId = activeBusiness?.businessId;
  const [data, setData] = useState<AccountingSummary | null>(null);
  const [move, setMove] = useState<MoneyMove | null>(null);
  const [catching, setCatching] = useState(false);

  const load = useCallback(async () => {
    if (!businessId) return;
    try { setData(await accountingApi.summary(businessId)); }
    catch (err) { toast.error(errorMessage(err)); }
  }, [businessId]);

  useEffect(() => { load(); }, [load]);

  const catchUp = async () => {
    if (!businessId) return;
    setCatching(true);
    try {
      const r = await accountingApi.catchUp(businessId);
      toast.success(`Posted ${r.invoicesPosted} invoices and ${r.paymentsPosted} payments to the books`
        + (r.skipped ? ` · ${r.skipped} need checking by hand` : ""));
      r.notes.slice(0, 5).forEach((n) => toast.info(n));
      load();
    } catch (err) { toast.error(errorMessage(err)); }
    setCatching(false);
  };

  if (!data || !businessId) return <PageLoader />;

  const month = new Date(data.monthStart).toLocaleDateString("en-GB", { month: "long", year: "numeric" });

  return (
    <div>
      <PageHeader title="Accounts" description="Your money, what's owed, and how the business is doing"
        action={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => setMove("capital")}><ArrowDownToLine className="h-4 w-4" /> Capital in</Button>
            <Button variant="outline" onClick={() => setMove("drawings")}><ArrowUpFromLine className="h-4 w-4" /> Drawings</Button>
            <Button onClick={() => setMove("transfer")}><ArrowLeftRight className="h-4 w-4" /> Move money</Button>
          </div>
        } />
      <AccountingTabs />

      {data.invoicesNotInBooks > 0 && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <span className="flex items-center gap-2"><AlertTriangle className="h-4 w-4" />
            {data.invoicesNotInBooks} sale/purchase invoice{data.invoicesNotInBooks === 1 ? " is" : "s are"} not in the books yet (made before accounts were set up).
          </span>
          <Button size="sm" variant="outline" onClick={catchUp} disabled={catching}>{catching ? "Posting…" : "Post them now"}</Button>
        </div>
      )}
      {data.pendingExpenses > 0 && (
        <Link href="/expenses" className="mb-4 flex items-center gap-2 rounded-2xl border border-line bg-surface px-4 py-3 text-sm text-ink-2 shadow-card hover:bg-surface-2">
          <Receipt className="h-4 w-4 text-accent-deep" /> {data.pendingExpenses} expense{data.pendingExpenses === 1 ? "" : "s"} waiting for approval →
        </Link>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <Panel title="Money" className="lg:col-span-2"
          action={<span className="text-sm text-ink-3">Total <Amount value={data.totalMoney} className="font-semibold text-ink" /></span>}>
          <div className="grid gap-3 sm:grid-cols-2">
            {data.money.map((m) => (
              <Link key={m.account.id} href={`/accounting/ledger?account=${m.account.id}`}
                className="rounded-2xl border border-line/70 bg-surface-2/50 p-4 transition-colors hover:bg-surface-2">
                <div className="text-sm font-medium text-ink-2">{m.account.name}</div>
                <div className="mt-1 text-2xl font-semibold tracking-tight text-ink"><Amount value={m.balance} /></div>
                <div className="mt-1 text-xs text-ink-3">{PURPOSE_HINT[m.account.purpose ?? ""] ?? m.account.code}</div>
              </Link>
            ))}
          </div>
        </Panel>

        <Panel title={`This month · ${month}`}>
          <dl className="space-y-3 text-sm">
            <div className="flex justify-between"><dt className="text-ink-3">Income</dt><dd><Amount value={data.monthIncome} /></dd></div>
            <div className="flex justify-between"><dt className="text-ink-3">Costs &amp; expenses</dt><dd><Amount value={-data.monthCosts} /></dd></div>
            <div className="flex justify-between border-t border-line pt-3 text-base font-semibold">
              <dt>{data.monthProfit >= 0 ? "Profit" : "Loss"}</dt><dd><Amount value={data.monthProfit} /></dd>
            </div>
          </dl>
          <Link href="/accounting/reports" className="mt-4 inline-block text-sm font-medium text-brand-deep hover:underline">Full profit &amp; loss →</Link>
        </Panel>

        <Stat label="Customers owe you" value={data.receivables} hint="Unpaid credit sales" />
        <Stat label="You owe suppliers" value={data.payables} hint="Unpaid credit purchases" />
        <Stat label="Stock value (at cost)" value={data.stockValue} hint={`Owner's capital ${formatCurrency(data.capital)}`} />
      </div>

      <Panel className="mt-4" title="Other entries">
        <p className="text-sm text-ink-3">Sales, purchases, payments, stock write-offs and approved expenses post to the books automatically.
          For anything else (opening balances, corrections, bank charges) add a manual entry.</p>
        <Link href="/accounting/journals?new=1" className="mt-3 inline-flex items-center gap-2 text-sm font-medium text-brand-deep hover:underline">
          <BookPlus className="h-4 w-4" /> Add a manual entry
        </Link>
      </Panel>

      {move && <MoneyMoveDialog businessId={businessId} mode={move} open onOpenChange={(o) => !o && setMove(null)} onDone={load} />}
    </div>
  );
}

function Stat({ label, value, hint }: { label: string; value: number; hint?: string }) {
  return (
    <div className="rounded-3xl border border-line/80 bg-surface p-5 shadow-card">
      <div className="text-sm font-medium text-ink-3">{label}</div>
      <div className="mt-1 text-2xl font-semibold tracking-tight text-ink"><Amount value={value} /></div>
      {hint && <div className="mt-1 text-xs text-ink-3">{hint}</div>}
    </div>
  );
}
