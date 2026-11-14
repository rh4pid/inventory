import { Badge } from "@/components/ui/badge";
import type { StockBatch } from "@/Types";

/** Expiry status of a batch, with days left / days past. */
export function BatchStatusBadge({ batch }: { batch: Pick<StockBatch, "status" | "daysToExpiry"> }) {
  const d = batch.daysToExpiry;
  switch (batch.status) {
    case "EXPIRED":
      return <Badge variant="destructive">Expired{d != null ? ` ${-d}d ago` : ""}</Badge>;
    case "EXPIRING":
      return <Badge variant="warning">{d === 0 ? "Expires today" : `Expires in ${d}d`}</Badge>;
    case "OK":
      return <Badge variant="success">{d != null ? `${d}d left` : "OK"}</Badge>;
    default:
      return <Badge variant="secondary">No expiry</Badge>;
  }
}

export const formatDate = (iso?: string) =>
  iso ? new Date(iso.length === 10 ? iso + "T00:00:00" : iso).toLocaleDateString() : "—";
