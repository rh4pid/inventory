"use client";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { sales as salesApi, purchases as purchasesApi } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PaymentStatusBadge, InvoiceStatusBadge } from "@/components/shared/StatusBadge";
import { formatCurrency, formatDate, errorMessage } from "@/lib/utils";
import { PAYMENT_METHODS } from "@/constants/Strings";
import type { Invoice, InvoiceDetailView, PaymentMethod } from "@/Types";
import { useCan } from "@/lib/access";

const MONEY_METHODS = PAYMENT_METHODS.filter((m) => m.value !== "CREDIT");
const fmtQty = (n: number) => (Number.isInteger(n) ? String(n) : n.toFixed(2));

type Mode = "view" | "pay" | "return" | "void";

/**
 * An invoice with its lines, payments and balance, plus what can be done to it: take a payment,
 * return items (customer return / back to supplier), or void it. Returns and voids are
 * manager-only (the server enforces this too).
 */
export function InvoiceDialog({ invoice, onClose, onChanged }: {
  invoice: Invoice;
  onClose: () => void;
  onChanged: () => void;
}) {
  const { activeBusiness } = useBusiness();
  const bizId = activeBusiness?.businessId ?? 0;
  const isSale = invoice.type === "SALES";
  const api = isSale ? salesApi : purchasesApi;
  const can = useCan();
  const isManager = can("RETURNS_AND_VOIDS");
  // Customers pay at the till; paying suppliers is for managers and admins.
  const canTakePayment = can(isSale ? "SELL" : "PAY_SUPPLIERS");

  const [view, setView] = useState<InvoiceDetailView | null>(null);
  const [mode, setMode] = useState<Mode>("view");
  const [busy, setBusy] = useState(false);
  const [payAmount, setPayAmount] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [returnQty, setReturnQty] = useState<Record<number, string>>({});
  const [reason, setReason] = useState("");

  const load = useCallback(async () => {
    try { setView(await api.detail(bizId, invoice.id)); }
    catch (err) { toast.error(errorMessage(err)); onClose(); }
  }, [api, bizId, invoice.id, onClose]);

  useEffect(() => { if (bizId) load(); }, [bizId, load]);

  if (!view) {
    return (
      <Dialog open onOpenChange={(o) => !o && onClose()}>
        <DialogContent><p className="py-6 text-sm text-gray-500">Loading…</p></DialogContent>
      </Dialog>
    );
  }

  const inv = view.invoice;
  const isVoid = inv.invoiceStatus === "VOID";
  const due = Number(view.balanceDue);
  const paid = Number(inv.amountPaid ?? 0);
  const returned = Number(view.returnedAmount ?? 0);
  const canReturn = isManager && !isVoid && view.lines.some((l) => l.returnable > 0);
  const party = isSale
    ? (inv.customer ? `${inv.customer.firstName} ${inv.customer.lastName ?? ""}` : "Walk-in customer")
    : (inv.supplier?.companyName ?? "No supplier");

  const done = async (msg: string) => {
    toast.success(msg);
    setMode("view");
    setReturnQty({});
    setReason("");
    setPayAmount("");
    await load();
    onChanged();
  };

  const pay = async () => {
    const amount = Number(payAmount);
    if (!(amount > 0)) return;
    setBusy(true);
    try {
      await api.recordPayment(bizId, inv.id, { amount, paymentMethod: method });
      await done("Payment recorded");
    } catch (err) { toast.error(errorMessage(err)); }
    finally { setBusy(false); }
  };

  const returnPicked = Object.entries(returnQty)
    .map(([id, q]) => ({ detailId: Number(id), quantity: Number(q) }))
    .filter((r) => r.quantity > 0);
  const returnValue = returnPicked.reduce((sum, r) => {
    const line = view.lines.find((l) => l.id === r.detailId)!;
    return sum + (Number(line.totalLine) / line.quantity) * r.quantity;
  }, 0);
  const tooMuch = returnPicked.some((r) => r.quantity > (view.lines.find((l) => l.id === r.detailId)?.returnable ?? 0) + 1e-9);

  const submitReturn = async () => {
    if (returnPicked.length === 0 || tooMuch) return;
    setBusy(true);
    try {
      await api.returnItems(bizId, inv.id, { items: returnPicked, reason: reason.trim() || undefined, refundMethod: method });
      await done(isSale ? "Return recorded, stock put back" : "Return to supplier recorded");
    } catch (err) { toast.error(errorMessage(err)); }
    finally { setBusy(false); }
  };

  const submitVoid = async () => {
    setBusy(true);
    try {
      await api.void(bizId, inv.id, paid > 0 ? method : undefined);
      await done(`${inv.invoiceNumber} voided`);
    } catch (err) { toast.error(errorMessage(err)); }
    finally { setBusy(false); }
  };

  const methodPicker = (label: string) => (
    <div className="space-y-1.5">
      <Label className="text-xs">{label}</Label>
      <Select value={method} onValueChange={(v) => setMethod(v as PaymentMethod)}>
        <SelectTrigger className="h-8 text-xs"><SelectValue /></SelectTrigger>
        <SelectContent>
          {MONEY_METHODS.map((m) => <SelectItem key={m.value} value={m.value}>{m.label}</SelectItem>)}
        </SelectContent>
      </Select>
    </div>
  );

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-base">{inv.invoiceNumber}</span>
            <InvoiceStatusBadge status={inv.invoiceStatus} />
            <PaymentStatusBadge status={inv.paymentStatus} />
          </DialogTitle>
        </DialogHeader>
        <p className="text-sm text-gray-500">
          {party} · {inv.shop?.shopName ?? "—"}{inv.createdAt ? ` · ${formatDate(inv.createdAt)}` : ""}
        </p>

        {/* Lines */}
        <div className="overflow-x-auto rounded-lg border border-gray-200">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-xs text-gray-500">
              <tr>
                <th className="px-3 py-2 text-left font-medium">Item</th>
                <th className="px-3 py-2 text-right font-medium">Qty</th>
                <th className="px-3 py-2 text-right font-medium">Price</th>
                <th className="px-3 py-2 text-right font-medium">Total</th>
                {mode === "return" && <th className="px-3 py-2 text-right font-medium">Return</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {view.lines.map((l) => (
                <tr key={l.id}>
                  <td className="px-3 py-2">
                    <div className="font-medium">{l.productName}</div>
                    {l.batchInfo && <div className="text-xs text-gray-400">Batch {l.batchInfo}</div>}
                    {l.returnedQuantity > 0 && (
                      <div className="text-xs text-amber-700">{fmtQty(l.returnedQuantity)} returned</div>
                    )}
                  </td>
                  <td className="px-3 py-2 text-right whitespace-nowrap">{fmtQty(l.quantity)} {l.unitName ?? ""}</td>
                  <td className="px-3 py-2 text-right">{formatCurrency(l.unitPrice)}</td>
                  <td className="px-3 py-2 text-right">{formatCurrency(l.totalLine)}</td>
                  {mode === "return" && (
                    <td className="px-3 py-2 text-right">
                      {l.returnable > 0 ? (
                        <Input
                          type="number" min="0" max={l.returnable} step="any" inputMode="decimal"
                          aria-label={`Quantity of ${l.productName} to return`}
                          className="ml-auto h-7 w-20 text-right text-xs"
                          placeholder={`max ${fmtQty(l.returnable)}`}
                          value={returnQty[l.id] ?? ""}
                          onChange={(e) => setReturnQty((q) => ({ ...q, [l.id]: e.target.value }))}
                        />
                      ) : <span className="text-xs text-gray-400">—</span>}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Totals */}
        <dl className="ml-auto grid w-full max-w-xs grid-cols-2 gap-y-1 text-sm">
          <dt className="text-gray-500">Total</dt><dd className="text-right">{formatCurrency(inv.totalAmount)}</dd>
          {returned > 0 && (<><dt className="text-gray-500">Returned</dt><dd className="text-right text-amber-700">−{formatCurrency(returned)}</dd></>)}
          <dt className="text-gray-500">Paid</dt><dd className="text-right">{formatCurrency(paid)}</dd>
          {!isVoid && (
            <>
              <dt className="font-semibold">{due < 0 ? (isSale ? "Owed to customer" : "Owed by supplier") : "Balance due"}</dt>
              <dd className="text-right font-semibold">{formatCurrency(Math.abs(due))}</dd>
            </>
          )}
        </dl>

        {/* Payments */}
        {view.payments.length > 0 && (
          <div>
            <h3 className="mb-1 text-xs font-semibold uppercase tracking-wide text-gray-500">Payments & refunds</h3>
            <ul className="divide-y divide-gray-100 rounded-lg border border-gray-200 text-sm">
              {view.payments.map((p) => {
                const amt = Number(p.amount);
                return (
                  <li key={p.id} className="flex items-center justify-between gap-3 px-3 py-2">
                    <div className="min-w-0">
                      <span className="font-medium">{amt < 0 ? "Refund" : "Payment"}</span>
                      <span className="text-gray-500"> · {MONEY_METHODS.find((m) => m.value === p.paymentMethod)?.label ?? p.paymentMethod}</span>
                      {p.notes && <div className="truncate text-xs text-gray-400">{p.notes}</div>}
                    </div>
                    <div className="shrink-0 text-right">
                      <div className={amt < 0 ? "text-amber-700" : ""}>{amt < 0 ? "−" : ""}{formatCurrency(Math.abs(amt))}</div>
                      {p.paidAt && <div className="text-xs text-gray-400">{formatDate(p.paidAt)}</div>}
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>
        )}

        {/* Action panels */}
        {mode === "pay" && (
          <div className="space-y-3 rounded-lg border border-gray-200 bg-gray-50 p-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Amount</Label>
                <Input type="number" min="0" step="0.01" inputMode="decimal" className="h-8 text-xs"
                  value={payAmount} onChange={(e) => setPayAmount(e.target.value)} />
              </div>
              {methodPicker(isSale ? "Paid by" : "Paid with")}
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" size="sm" onClick={() => setMode("view")}>Cancel</Button>
              <Button size="sm" onClick={pay} disabled={busy || !(Number(payAmount) > 0) || Number(payAmount) > due + 1e-9}>
                {busy ? "Saving…" : "Record payment"}
              </Button>
            </div>
          </div>
        )}

        {mode === "return" && (
          <div className="space-y-3 rounded-lg border border-amber-200 bg-amber-50 p-3">
            <p className="text-sm text-amber-900">
              {isSale
                ? "Returned items go back into stock (into the batch they came from). If the customer has paid more than they now owe, the difference is refunded."
                : "Returned items are taken out of stock from the batch this purchase created. What you owe the supplier goes down; if you already paid, they owe you a refund."}
            </p>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label className="text-xs">Reason</Label>
                <Input className="h-8 text-xs" placeholder="e.g. Damaged, wrong item" value={reason} onChange={(e) => setReason(e.target.value)} />
              </div>
              {methodPicker("Refund by (if any)")}
            </div>
            {tooMuch && <p className="text-xs text-red-600">A quantity is more than is left to return.</p>}
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm text-gray-600">≈ {formatCurrency(returnValue)} before tax/discount</span>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={() => { setMode("view"); setReturnQty({}); }}>Cancel</Button>
                <Button size="sm" onClick={submitReturn} disabled={busy || returnPicked.length === 0 || tooMuch}>
                  {busy ? "Saving…" : "Record return"}
                </Button>
              </div>
            </div>
          </div>
        )}

        {mode === "void" && (
          <div className="space-y-3 rounded-lg border border-red-200 bg-red-50 p-3">
            <p className="text-sm text-red-900">
              Voiding cancels {inv.invoiceNumber} completely.{" "}
              {isSale ? "Items not already returned go back into stock." : "Stock it added (and not already returned) is removed."}{" "}
              {paid > 0 ? `${formatCurrency(paid)} paid will be recorded as refunded.` : ""} The accounting entries are reversed. This cannot be undone.
            </p>
            {paid > 0 && <div className="max-w-xs">{methodPicker("Refund by")}</div>}
            <div className="flex justify-end gap-2">
              <Button variant="outline" size="sm" onClick={() => setMode("view")}>Cancel</Button>
              <Button size="sm" variant="destructive" onClick={submitVoid} disabled={busy}>
                {busy ? "Voiding…" : "Void invoice"}
              </Button>
            </div>
          </div>
        )}

        {mode === "view" && (
          <div className="flex flex-wrap justify-end gap-2">
            {!isVoid && due > 0 && canTakePayment && (
              <Button size="sm" onClick={() => { setMethod("CASH"); setPayAmount(due.toFixed(2)); setMode("pay"); }}>
                Record payment
              </Button>
            )}
            {canReturn && (
              <Button size="sm" variant="outline" onClick={() => { setMethod(inv.paymentMethod !== "CREDIT" ? inv.paymentMethod : "CASH"); setMode("return"); }}>
                {isSale ? "Customer return" : "Return to supplier"}
              </Button>
            )}
            {isManager && !isVoid && (
              <Button size="sm" variant="outline" className="text-red-600 hover:text-red-700"
                onClick={() => { setMethod(inv.paymentMethod !== "CREDIT" ? inv.paymentMethod : "CASH"); setMode("void"); }}>
                Void
              </Button>
            )}
            <Button size="sm" variant="ghost" onClick={onClose}>Close</Button>
          </div>
        )}
        {!isManager && !isVoid && (
          <p className="text-xs text-gray-400">Returns and voids need a manager.</p>
        )}
      </DialogContent>
    </Dialog>
  );
}
