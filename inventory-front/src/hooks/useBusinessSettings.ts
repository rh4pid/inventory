"use client";
import { useCallback, useEffect, useState } from "react";
import { useBusiness } from "@/context/BusinessContext";
import { settings as settingsApi } from "@/lib/api";
import type { BusinessSettings } from "@/Types";

/** Feature switches for the active business (pack sizes, batches, approvals…). */
export function useBusinessSettings() {
  const { activeBusiness } = useBusiness();
  const [settings, setSettings] = useState<BusinessSettings | null>(null);

  const reload = useCallback(async () => {
    if (!activeBusiness) return;
    try {
      setSettings(await settingsApi.get(activeBusiness.businessId));
    } catch {
      setSettings(null);
    }
  }, [activeBusiness]);

  useEffect(() => { reload(); }, [reload]);

  return { settings, setSettings, reload };
}
