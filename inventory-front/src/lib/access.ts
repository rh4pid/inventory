"use client";
import { useCallback } from "react";
import { useBusiness } from "@/context/BusinessContext";

/**
 * What a role allows. The server decides (Constants/Capability.java) and sends the list with each
 * membership; the app only uses it to show the right menus, pages and buttons. The server checks
 * every request again, so hiding something here is convenience, not security.
 */
export type Capability =
  | "MANAGE_SETTINGS" | "MANAGE_ACCOUNTS" | "MANAGE_PAYROLL" | "MANAGE_TEAM" | "MANAGE_LOCATIONS"
  | "MANAGE_CATALOG" | "MANAGE_SUPPLIERS" | "DELETE_CUSTOMERS" | "VIEW_STOCK" | "VIEW_COSTS"
  | "SELL" | "MANAGE_CUSTOMERS" | "RECEIVE" | "PAY_SUPPLIERS"
  | "STOCK_IN" | "STOCK_OUT" | "ADJUST" | "TRANSFER" | "COUNT" | "APPROVE" | "RECORD_EXPENSES" | "RETURNS_AND_VOIDS";

/** `can("SELL")`, or `can(["RECEIVE", "MANAGE_SUPPLIERS"])` for any of several. */
export function useCan() {
  const { activeBusiness } = useBusiness();
  const caps = activeBusiness?.capabilities;
  return useCallback((need?: Capability | Capability[]) => {
    if (!need) return true;
    const list = Array.isArray(need) ? need : [need];
    return list.some((c) => caps?.includes(c));
  }, [caps]);
}

/** Anyone who can upload at least one kind of spreadsheet (see the backend importers). */
export const UPLOAD_CAPS: Capability[] = [
  "MANAGE_CATALOG", "MANAGE_SUPPLIERS", "MANAGE_PAYROLL", "MANAGE_CUSTOMERS", "MANAGE_LOCATIONS", "MANAGE_TEAM", "MANAGE_ACCOUNTS",
];

/** Pages and what they need (longest prefix wins). Pages not listed are open to every member. */
export const PAGE_RULES: { prefix: string; need: Capability | Capability[] }[] = [
  { prefix: "/pos", need: "SELL" },
  { prefix: "/templates", need: UPLOAD_CAPS },
  { prefix: "/sales", need: "SELL" },
  { prefix: "/purchases", need: "RECEIVE" },
  { prefix: "/customers", need: "MANAGE_CUSTOMERS" },
  { prefix: "/suppliers", need: "MANAGE_SUPPLIERS" },
  { prefix: "/payroll", need: "MANAGE_PAYROLL" },
  { prefix: "/accounting", need: "MANAGE_ACCOUNTS" },
  { prefix: "/expenses", need: ["RECORD_EXPENSES", "APPROVE"] },
  { prefix: "/categories", need: "MANAGE_CATALOG" },
  { prefix: "/products/new", need: "MANAGE_CATALOG" },
  { prefix: "/inventory/counts", need: "COUNT" },
  { prefix: "/settings/team", need: "MANAGE_TEAM" },
  { prefix: "/settings/features", need: "MANAGE_SETTINGS" },
  { prefix: "/settings/business", need: "MANAGE_SETTINGS" },
  { prefix: "/settings/appearance", need: "MANAGE_SETTINGS" },
  { prefix: "/settings/accounts", need: "MANAGE_ACCOUNTS" },
  { prefix: "/settings/units", need: "MANAGE_CATALOG" },
  { prefix: "/settings/locations", need: "MANAGE_LOCATIONS" },
];

export function pageNeed(pathname: string): Capability | Capability[] | undefined {
  if (/^\/products\/[^/]+\/edit/.test(pathname)) return "MANAGE_CATALOG";
  const match = PAGE_RULES
    .filter((r) => pathname === r.prefix || pathname.startsWith(r.prefix + "/"))
    .sort((a, b) => b.prefix.length - a.prefix.length)[0];
  return match?.need;
}

export const ROLE_LABEL: Record<string, string> = {
  OWNER: "Owner", ADMIN: "Admin", MANAGER: "Manager", CASHIER: "Cashier", STAFF: "Staff", DRIVER: "Driver", SHOPKEEP: "Cashier",
};
