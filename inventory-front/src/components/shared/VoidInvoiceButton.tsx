"use client";
import { useState } from "react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { sales as salesApi, purchases as purchasesApi } from "@/lib/api";
import { Button } from "@/components/ui/button";
import type { Invoice } from "@/Types";

/**
 * Void action for a sale or purchase. Shown to managers only, and only for invoices with
 * nothing paid (the server enforces both). Voiding a sale returns its stock; voiding a
 * purchase removes the stock it added.
 */
export function VoidInvoiceButton({ invoice, onDone }: { invoice: Invoice; onDone: () => void }) {
  const { activeBusiness } = useBusiness();
  const [busy, setBusy] = useState(false);
  const isManager = ["OWNER", "ADMIN", "MANAGER"].includes(activeBusiness?.role ?? "");
  const unpaid = Number(invoice.amountPaid ?? 0) === 0;
  if (!activeBusiness || !isManager || invoice.invoiceStatus === "VOID" || !unpaid) return null;

  const isSale = invoice.type === "SALES";
  const run = async () => {
    const effect = isSale ? "Its stock will be put back." : "The stock it added will be removed.";
    if (!window.confirm(`Void ${invoice.invoiceNumber}? ${effect} This cannot be undone.`)) return;
    setBusy(true);
    try {
      if (isSale) await salesApi.void(activeBusiness.businessId, invoice.id);
      else await purchasesApi.void(activeBusiness.businessId, invoice.id);
      toast.success(`${invoice.invoiceNumber} voided`);
      onDone();
    } catch (err: any) {
      toast.error(err.message ?? "Could not void");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Button variant="ghost" size="sm" onClick={run} disabled={busy} className="text-red-600 hover:text-red-700">
      {busy ? "Voiding…" : "Void"}
    </Button>
  );
}
