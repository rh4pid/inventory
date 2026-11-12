"use client";
import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { shops as shopsApi } from "@/lib/api";
import { useBusiness } from "./BusinessContext";
import type { Shop } from "@/Types";

export type Period = "week" | "month" | "quarter" | "year";

export const PERIODS: { value: Period; label: string; days: number }[] = [
  { value: "week", label: "Week", days: 7 },
  { value: "month", label: "Month", days: 30 },
  { value: "quarter", label: "Quarter", days: 90 },
  { value: "year", label: "Year", days: 365 },
];

interface WorkspaceContextType {
  /** Locations the current user works at (all of them for managers). */
  locations: Shop[];
  /** The location picked in the header; null = all my locations. */
  locationId: number | null;
  location: Shop | null;
  setLocationId: (id: number | null) => void;
  period: Period;
  setPeriod: (p: Period) => void;
  refreshLocations: () => Promise<void>;
}

const WorkspaceContext = createContext<WorkspaceContextType | null>(null);

const read = (key: string) => {
  try { return typeof window !== "undefined" ? localStorage.getItem(key) : null; } catch { return null; }
};
const write = (key: string, value: string | null) => {
  try {
    if (value == null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch { /* storage unavailable: the choice just isn't remembered */ }
};

/** The location and period the user is looking at, shared by the header and the pages. */
export function WorkspaceProvider({ children }: { children: React.ReactNode }) {
  const { activeBusiness } = useBusiness();
  const businessId = activeBusiness?.businessId;
  const [locations, setLocations] = useState<Shop[]>([]);
  const [locationId, setLocationIdState] = useState<number | null>(null);
  const [period, setPeriodState] = useState<Period>("month");

  // Remembered period, read after mount so server and client render the same first frame.
  useEffect(() => {
    const p = read("period");
    if (p && PERIODS.some((x) => x.value === p)) setPeriodState(p as Period);
  }, []);

  const refreshLocations = useCallback(async () => {
    if (!businessId) { setLocations([]); return; }
    try {
      const list = (await shopsApi.list(businessId, { mine: true })).content;
      setLocations(list);
      const stored = read(`location:${businessId}`);
      setLocationIdState(stored && list.some((s) => String(s.id) === stored) ? Number(stored) : null);
    } catch {
      setLocations([]);
    }
  }, [businessId]);

  useEffect(() => { refreshLocations(); }, [refreshLocations]);

  const setLocationId = useCallback((id: number | null) => {
    setLocationIdState(id);
    if (businessId) write(`location:${businessId}`, id == null ? null : String(id));
  }, [businessId]);

  const setPeriod = useCallback((p: Period) => {
    setPeriodState(p);
    write("period", p);
  }, []);

  const location = locations.find((s) => s.id === locationId) ?? null;

  return (
    <WorkspaceContext.Provider value={{
      locations, locationId, location, setLocationId, period, setPeriod, refreshLocations,
    }}>
      {children}
    </WorkspaceContext.Provider>
  );
}

export function useWorkspace() {
  const ctx = useContext(WorkspaceContext);
  if (!ctx) throw new Error("useWorkspace must be used inside WorkspaceProvider");
  return ctx;
}
