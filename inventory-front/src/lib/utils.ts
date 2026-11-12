import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** The active company's currency symbol; set from its settings (SettingsProvider). */
let currencySymbol = "$";
export function setCurrencySymbol(symbol?: string | null) {
  currencySymbol = symbol && symbol.trim() ? symbol.trim() : "$";
}

/** Money in the company's currency, e.g. "GH₵1,250.00". Pass `symbol` only to override. */
export function formatCurrency(amount: number | string | null | undefined, symbol = currencySymbol): string {
  const num = typeof amount === "string" ? parseFloat(amount) : amount ?? NaN;
  const value = Number.isNaN(num) ? 0 : num;
  const sign = value < 0 ? "-" : "";
  return `${sign}${symbol}${Math.abs(value).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function formatDate(date: string | Date): string {
  return new Date(date).toLocaleDateString("en-US", {
    year: "numeric", month: "short", day: "numeric",
  });
}

export function truncate(str: string, n = 30): string {
  return str.length > n ? str.slice(0, n) + "…" : str;
}

/** A readable message from anything thrown (API errors, network failures…). */
export function errorMessage(err: unknown, fallback = "Something went wrong"): string {
  if (err instanceof Error && err.message) return err.message;
  if (typeof err === "string" && err) return err;
  if (err && typeof err === "object" && "message" in err && typeof (err as { message: unknown }).message === "string") {
    return (err as { message: string }).message;
  }
  return fallback;
}
