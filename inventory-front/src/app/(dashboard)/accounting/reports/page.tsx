"use client";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Printer, CheckCircle2, AlertTriangle } from "lucide-react";
import { useBusiness } from "@/context/BusinessContext";
import { accounting as accountingApi } from "@/lib/api";
import { cn, errorMessage, formatCurrency } from "@/lib/utils";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AccountingTabs, Amount, Panel, monthStart, today, yearStart } from "@/components/accounting/shared";
import type { BalanceSheet, ProfitAndLoss, ReportRow, TrialBalance } from "@/Types";

type Report = "pnl" | "balance" | "trial";

export default function ReportsPage() {
  const { activeBusiness } = useBusiness();
  const businessId = activeBusiness?.businessId;
  const [report, setReport] = useState<Report>("pnl");
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(today());
  const [pnl, setPnl] = useState<ProfitAndLoss | null>(null);
  const [bs, setBs] = useState<BalanceSheet | null>(null);
  const [tb, setTb] = useState<TrialBalance | null>(null);

  const load = useCallback(async () => {
    if (!businessId) return;
    try {
      if (report === "pnl") setPnl(await accountingApi.profitLoss(businessId, { from, to }));
      if (report === "balance") setBs(await accountingApi.balanceSheet(businessId, to));
      if (report === "trial") setTb(await accountingApi.trialBalance(businessId, to));
    } catch (err) { toast.error(errorMessage(err)); }
  }, [businessId, report, from, to]);

  useEffect(() => { load(); }, [load]);

  return (
    <div>
      <PageHeader title="Reports" description={activeBusiness?.businessName}
        action={<Button variant="outline" onClick={() => window.print()} className="print:hidden"><Printer className="h-4 w-4" /> Print</Button>} />
      <AccountingTabs />

      <div className="mb-4 flex flex-wrap items-end gap-3 print:hidden">
        <Tabs value={report} onValueChange={(v) => setReport(v as Report)}>
          <TabsList>
            <TabsTrigger value="pnl">Profit &amp; loss</TabsTrigger>
            <TabsTrigger value="balance">Balance sheet</TabsTrigger>
            <TabsTrigger value="trial">Trial balance</TabsTrigger>
          </TabsList>
        </Tabs>
        {report === "pnl" && (
          <div className="space-y-1.5">
            <Label htmlFor="r-from">From</Label>
            <Input id="r-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
        )}
        <div className="space-y-1.5">
          <Label htmlFor="r-to">{report === "pnl" ? "To" : "As of"}</Label>
          <Input id="r-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        {report === "pnl" && (
          <div className="flex gap-2">
            <Button variant="ghost" size="sm" onClick={() => { setFrom(monthStart()); setTo(today()); }}>This month</Button>
            <Button variant="ghost" size="sm" onClick={() => { setFrom(yearStart()); setTo(today()); }}>This year</Button>
          </div>
        )}
      </div>

      {report === "pnl" && pnl && (
        <Panel title={`Profit & loss · ${pnl.from} to ${pnl.to}`} className="max-w-3xl">
          <Section title="Income" rows={pnl.income} total={pnl.totalIncome} totalLabel="Total income" />
          <Section title="Cost of sales" rows={pnl.costOfSales} total={pnl.totalCostOfSales} totalLabel="Total cost of sales" />
          <Line label="Gross profit" value={pnl.grossProfit} strong />
          <Section title="Expenses" rows={pnl.expenses} total={pnl.totalExpenses} totalLabel="Total expenses" />
          <Line label={pnl.netProfit >= 0 ? "Net profit" : "Net loss"} value={pnl.netProfit} strong big />
        </Panel>
      )}

      {report === "balance" && bs && (
        <Panel title={`Balance sheet · as of ${bs.asOf}`} className="max-w-3xl"
          action={<Check ok={bs.balanced} text={bs.balanced ? "Balances" : "Doesn't balance"} />}>
          <Section title="Assets (what the business has)" rows={bs.assets} total={bs.totalAssets} totalLabel="Total assets" />
          <Section title="Liabilities (what it owes)" rows={bs.liabilities} total={bs.totalLiabilities} totalLabel="Total liabilities" />
          <Section title="Equity (the owner's share)" rows={bs.equity} extra={{ label: "Profit to date", value: bs.profitToDate }}
            total={bs.totalEquity} totalLabel="Total equity" />
          <Line label="Liabilities + equity" value={bs.totalLiabilitiesAndEquity} strong big />
        </Panel>
      )}

      {report === "trial" && tb && (
        <Panel title={`Trial balance · as of ${tb.asOf}`} className="max-w-3xl"
          action={<Check ok={tb.balanced} text={tb.balanced ? "Debits = credits" : "Out of balance"} />}>
          <table className="w-full text-sm">
            <thead className="text-xs text-ink-3">
              <tr><th className="pb-2 text-left font-medium">Account</th><th className="w-36 pb-2 text-right font-medium">Debit</th><th className="w-36 pb-2 text-right font-medium">Credit</th></tr>
            </thead>
            <tbody>
              {tb.rows.map((r) => (
                <tr key={r.account.id} className="border-t border-line/60">
                  <td className="py-1.5"><AccountLink row={r.account} /></td>
                  <td className="py-1.5 text-right tabular-nums">{Number(r.debit) ? formatCurrency(r.debit) : ""}</td>
                  <td className="py-1.5 text-right tabular-nums">{Number(r.credit) ? formatCurrency(r.credit) : ""}</td>
                </tr>
              ))}
              <tr className="border-t-2 border-ink/20 font-semibold">
                <td className="py-2">Totals</td>
                <td className="py-2 text-right tabular-nums">{formatCurrency(tb.totalDebit)}</td>
                <td className="py-2 text-right tabular-nums">{formatCurrency(tb.totalCredit)}</td>
              </tr>
            </tbody>
          </table>
        </Panel>
      )}
    </div>
  );
}

function AccountLink({ row }: { row: ReportRow["account"] }) {
  return (
    <Link href={`/accounting/ledger?account=${row.id}`} className="hover:underline">
      <span className="font-mono text-xs text-ink-3">{row.code}</span> {row.name}
    </Link>
  );
}

function Section({ title, rows, total, totalLabel, extra }: {
  title: string; rows: ReportRow[]; total: number; totalLabel: string; extra?: { label: string; value: number };
}) {
  return (
    <div className="mb-5">
      <h3 className="mb-1 text-xs font-semibold uppercase tracking-wide text-ink-3">{title}</h3>
      {rows.length === 0 && !extra && <p className="py-1 text-sm text-ink-3">None</p>}
      {rows.map((r) => (
        <div key={r.account.id} className="flex justify-between border-t border-line/60 py-1.5 text-sm">
          <AccountLink row={r.account} /><Amount value={r.amount} />
        </div>
      ))}
      {extra && (
        <div className="flex justify-between border-t border-line/60 py-1.5 text-sm">
          <span>{extra.label}</span><Amount value={extra.value} />
        </div>
      )}
      <Line label={totalLabel} value={total} />
    </div>
  );
}

function Line({ label, value, strong, big }: { label: string; value: number; strong?: boolean; big?: boolean }) {
  return (
    <div className={cn("flex justify-between border-t border-line py-2 text-sm font-medium",
      strong && "font-semibold", big && "mb-2 border-t-2 border-ink/20 text-base")}>
      <span>{label}</span><Amount value={value} />
    </div>
  );
}

function Check({ ok, text }: { ok: boolean; text: string }) {
  return (
    <span className={cn("inline-flex items-center gap-1.5 text-xs font-medium", ok ? "text-green-700" : "text-red-600")}>
      {ok ? <CheckCircle2 className="h-4 w-4" /> : <AlertTriangle className="h-4 w-4" />} {text}
    </span>
  );
}
