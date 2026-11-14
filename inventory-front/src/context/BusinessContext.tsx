"use client";
import { createContext, useContext, useState, useEffect, useCallback } from "react";
import { users } from "@/lib/api";
import type { BusinessMembership } from "@/Types";
import { useAuth } from "./AuthProvider";

interface BusinessContextType {
  businesses: BusinessMembership[];
  activeBusiness: BusinessMembership | null;
  loadingBusinesses: boolean;
  /** True once the list has been fetched for the signed-in user (an empty list then means "no company yet"). */
  businessesLoaded: boolean;
  switchBusiness: (b: BusinessMembership) => void;
  /** Re-fetch the list; pass an id to switch to that company afterwards (e.g. one you just created). */
  refreshBusinesses: (selectId?: number) => Promise<void>;
}

const BusinessContext = createContext<BusinessContextType | null>(null);

export function BusinessProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [businesses, setBusinesses] = useState<BusinessMembership[]>([]);
  const [activeBusiness, setActiveBusiness] = useState<BusinessMembership | null>(null);
  const [loadingBusinesses, setLoadingBusinesses] = useState(false);
  const [businessesLoaded, setBusinessesLoaded] = useState(false);

  const fetchBusinesses = useCallback(async (selectId?: number) => {
    setLoadingBusinesses(true);
    try {
      const list = await users.businesses();
      setBusinesses(list);
      setActiveBusiness((prev) => {
        const byId = (id: string | number | null | undefined) =>
          id == null ? undefined : list.find((b) => String(b.businessId) === String(id));
        const stored = typeof window !== "undefined" ? localStorage.getItem("activeBusinessId") : null;
        // The fresh copy (your role may have changed); a company you were removed from drops out.
        const next = byId(selectId) ?? byId(prev?.businessId) ?? byId(stored) ?? list[0] ?? null;
        if (next && typeof window !== "undefined") localStorage.setItem("activeBusinessId", String(next.businessId));
        return next;
      });
      setBusinessesLoaded(true);
    } catch {}
    setLoadingBusinesses(false);
  }, []);

  useEffect(() => {
    if (user) fetchBusinesses();
    else {
      setBusinesses([]);
      setActiveBusiness(null);
      setBusinessesLoaded(false);
    }
  }, [user, fetchBusinesses]);

  const switchBusiness = useCallback((b: BusinessMembership) => {
    setActiveBusiness(b);
    if (typeof window !== "undefined") {
      localStorage.setItem("activeBusinessId", String(b.businessId));
    }
  }, []);

  return (
    <BusinessContext.Provider value={{
      businesses, activeBusiness, loadingBusinesses, businessesLoaded,
      switchBusiness, refreshBusinesses: fetchBusinesses,
    }}>
      {children}
    </BusinessContext.Provider>
  );
}

export function useBusiness() {
  const ctx = useContext(BusinessContext);
  if (!ctx) throw new Error("useBusiness must be used within BusinessProvider");
  return ctx;
}
