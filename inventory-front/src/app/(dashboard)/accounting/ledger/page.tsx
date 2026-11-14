"use client";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { accounting as accountingApi, accounts as accountsApi } from "@/lib/api";
import { errorMessage, formatCurrency } from "@/lib/utils";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AccountingTabs, AccountSelect, Amount, monthStart, today } from "@/components/accounting/shared";
import type { Account, Ledger } from "@/Types";

export default function LedgerPage() {
  const { activeBusiness } = useBusiness();
  const businessId = activeBusiness?.businessId;
  const [accounts, setAccounts] = useState<Account[] | null>(null);
  const [accountId, setAccountId] = useState("");
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(today());
  const [ledger, setLedger] = useState<Ledger | null>(null);

  useEffect(() => {
    if (!businessId) return;
    accountsApi.list(businessId).then((list) => {
      setAccounts(list);
      const wanted = new URLSearchParams(window.location.search).get("account");
      const pick = list.find((a) => String(a.id) === wanted) ?? list.find((a) => a.accountPurpose === "CASH") ?? list[0];
      if (pick) setAccountId(String(pick.id));
    }).catch((err) => toast.error(errorMessage(err)));
  }, [businessId]);

  const load = useCallback(async () => {
    if (!businessId || !accountId) return;
    try { setLedger(await accountingApi.ledger(businessId, Number(accountId), { from, to })); }
    catch (err) { toast.error(errorMessage(err)); }
  }, [businessId, accountId, from, to]);

  useEffect(() => { load(); }, [load]);

  if (!accounts || !businessId) return <PageLoader />;

  return (
    <div>
      <PageHeader title="Ledger" description="Everything that went in and out of one account" />
      <AccountingTabs />

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="w-full space-y-1.5 sm:w-72">
          <Label htmlFor="l-acc">Account</Label>
          <AccountSelect id="l-acc" accounts={accounts.map((a) => ({ id: a.id, code: a.accountCode, name: a.accountName }))}
            value={accountId} onChange={setAccountId} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="l-from">From</Label>
          <Input id="l-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="l-to">To</Label>
          <Input id="l-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
      </div>

      {ledger && (
        <div className="overflow-hidden rounded-3xl border border-line/80 bg-surface shadow-card">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-40">Date</TableHead>
                  <TableHead className="w-40">Reference</TableHead>
                  <TableHead>Details</TableHead>
                  <TableHead className="w-32 text-right">In (debit)</TableHead>
                  <TableHead className="w-32 text-right">Out (credit)</TableHead>
                  <TableHead className="w-36 text-right">Balance</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow className="bg-surface-2/60">
                  <TableCell colSpan={5} className="text-sm font-medium text-ink-2">Opening balance on {ledger.from}</TableCell>
                  <TableCell className="text-right font-medium"><Amount value={ledger.opening} /></TableCell>
                </TableRow>
                {ledger.lines.length === 0 && (
                  <TableRow><TableCell colSpan={6} className="py-8 text-center text-sm text-ink-3">No entries in this period.</TableCell></TableRow>
                )}
                {ledger.lines.map((l, i) => (
                  <TableRow key={i}>
                    <TableCell className="text-sm text-ink-3">{new Date(l.date).toLocaleString("en-GB", { dateStyle: "medium", timeStyle: "short" })}</TableCell>
                    <TableCell className="font-mono text-xs text-ink-3">{l.reference}</TableCell>
                    <TableCell className="text-sm">{l.description}</TableCell>
                    <TableCell className="text-right text-sm tabular-nums">{Number(l.debit) > 0 ? formatCurrency(l.debit) : ""}</TableCell>
                    <TableCell className="text-right text-sm tabular-nums">{Number(l.credit) > 0 ? formatCurrency(l.credit) : ""}</TableCell>
                    <TableCell className="text-right text-sm"><Amount value={l.balance} /></TableCell>
                  </TableRow>
                ))}
                <TableRow className="bg-surface-2/60 font-medium">
                  <TableCell colSpan={3} className="text-sm">Closing balance on {ledger.to}</TableCell>
                  <TableCell className="text-right text-sm tabular-nums">{formatCurrency(ledger.totalDebit)}</TableCell>
                  <TableCell className="text-right text-sm tabular-nums">{formatCurrency(ledger.totalCredit)}</TableCell>
                  <TableCell className="text-right"><Amount value={ledger.closing} /></TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </div>
      )}
    </div>
  );
}
