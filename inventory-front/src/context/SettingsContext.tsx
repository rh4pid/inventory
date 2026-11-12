"use client";
import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { useBusiness } from "./BusinessContext";
import { settings as settingsApi } from "@/lib/api";
import { setCurrencySymbol } from "@/lib/utils";
import { applyTheme, clearTheme, resolveTheme, themeById } from "@/lib/themes";
import type { BusinessSettings } from "@/Types";

interface SettingsContextType {
  settings: BusinessSettings | null;
  setSettings: (s: BusinessSettings) => void;
  reload: () => Promise<void>;
}

const SettingsContext = createContext<SettingsContextType | null>(null);

const cacheKey = (id: number) => `theme:${id}`;

/**
 * The active company's settings, loaded once and shared (feature switches, currency, theme).
 * Also applies the company's colour theme and currency to the whole app.
 */
export function SettingsProvider({ children }: { children: React.ReactNode }) {
  const { activeBusiness } = useBusiness();
  const businessId = activeBusiness?.businessId;
  const [settings, setSettingsState] = useState<BusinessSettings | null>(null);

  // Paint the last known theme straight away (no flash of the default colours while loading).
  useEffect(() => {
    if (!businessId) return;
    try {
      const cached = themeById(localStorage.getItem(cacheKey(businessId)));
      if (cached) applyTheme(cached);
    } catch { /* storage unavailable */ }
  }, [businessId]);

  const adopt = useCallback((s: BusinessSettings | null) => {
    setSettingsState(s);
    if (!s) return;
    setCurrencySymbol(s.currencySymbol);
    const theme = resolveTheme(s.theme, s.businessType);
    applyTheme(theme);
    try { if (businessId) localStorage.setItem(cacheKey(businessId), theme.id); } catch { /* not remembered */ }
  }, [businessId]);

  const reload = useCallback(async () => {
    if (!businessId) return;
    try { adopt(await settingsApi.get(businessId)); }
    catch { setSettingsState(null); }
  }, [businessId, adopt]);

  useEffect(() => { reload(); }, [reload]);

  // Leaving the company app (sign-out, admin console): back to the default look.
  useEffect(() => () => { clearTheme(); setCurrencySymbol("$"); }, []);

  return (
    <SettingsContext.Provider value={{ settings, setSettings: adopt, reload }}>
      {children}
    </SettingsContext.Provider>
  );
}

export function useSettingsContext() {
  return useContext(SettingsContext);
}
