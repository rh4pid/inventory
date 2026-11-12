"use client";
import { useSettingsContext } from "@/context/SettingsContext";
import type { BusinessSettings } from "@/Types";

/**
 * Feature switches for the active business (pack sizes, batches, approvals, currency, theme).
 * Loaded once by SettingsProvider and shared by every component.
 */
export function useBusinessSettings(): {
  settings: BusinessSettings | null;
  setSettings: (s: BusinessSettings) => void;
  reload: () => Promise<void>;
} {
  const ctx = useSettingsContext();
  if (!ctx) throw new Error("useBusinessSettings must be used inside SettingsProvider");
  return ctx;
}
