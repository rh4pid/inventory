import type { LocationType, StockCountStatus, StockMovementType } from "@/Types";

export const LOCATION_TYPES: { value: LocationType; label: string }[] = [
  { value: "SHOP",      label: "Shop / sales point" },
  { value: "WAREHOUSE", label: "Warehouse" },
  { value: "STOREROOM", label: "Storeroom" },
  { value: "PHARMACY",  label: "Pharmacy / dispensary" },
  { value: "VEHICLE",   label: "Vehicle / van" },
  { value: "OTHER",     label: "Other" },
];

export const locationTypeLabel = (t?: LocationType) =>
  LOCATION_TYPES.find((l) => l.value === (t ?? "SHOP"))?.label ?? "Shop";

export const MOVEMENT_LABELS: Record<StockMovementType, string> = {
  STOCK_IN: "Stock in",
  STOCK_OUT: "Stock out",
  TRANSFER_IN: "Transfer in",
  TRANSFER_OUT: "Transfer out",
  ADJUSTMENT: "Adjustment",
  SALE: "Sale",
  SALE_RETURN: "Sale return",
  PURCHASE_RETURN: "Purchase return",
  OPENING_BALANCE: "Opening balance",
};

/** Reasons offered when stock leaves without a sale. */
export const STOCK_OUT_REASONS = [
  "Damaged", "Expired", "Theft / loss", "Internal use", "Returned to supplier", "Sample / giveaway", "Other",
];

export const ADJUSTMENT_REASONS = [
  "Stock count correction", "Data entry error", "Found stock", "Damaged", "Expired", "Theft / loss", "Other",
];

export const fmtQty = (n: number) =>
  Number.isInteger(n) ? String(n) : n.toFixed(3).replace(/0+$/, "").replace(/\.$/, "");

export const COUNT_STATUS: Record<StockCountStatus, { label: string; variant: "default" | "warning" | "success" | "secondary" }> = {
  OPEN: { label: "Counting", variant: "default" },
  SUBMITTED: { label: "Awaiting manager", variant: "warning" },
  POSTED: { label: "Posted", variant: "success" },
  CANCELLED: { label: "Cancelled", variant: "secondary" },
};
