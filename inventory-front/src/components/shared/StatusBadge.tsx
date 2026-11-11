import { Badge } from "@/components/ui/badge";
import type { PaymentStatus, InvoiceStatus } from "@/Types";

export function PaymentStatusBadge({ status }: { status: PaymentStatus }) {
  const map: Record<PaymentStatus, { variant: "success" | "warning" | "destructive" | "secondary"; label: string }> = {
    PAID:    { variant: "success",     label: "Paid" },
    PARTIAL: { variant: "warning",     label: "Partial" },
    UNPAID:  { variant: "destructive", label: "Unpaid" },
    VOID:    { variant: "secondary",   label: "Void" },
  };
  const { variant, label } = map[status] ?? { variant: "secondary", label: status };
  return <Badge variant={variant}>{label}</Badge>;
}

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  const map: Record<InvoiceStatus, { variant: "success" | "warning" | "secondary"; label: string }> = {
    COMPLETED: { variant: "success",   label: "Completed" },
    PENDING:   { variant: "warning",   label: "Pending" },
    VOID:      { variant: "secondary", label: "Void" },
  };
  const { variant, label } = map[status] ?? { variant: "secondary", label: status };
  return <Badge variant={variant}>{label}</Badge>;
}
