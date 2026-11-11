"use client";
import { createContext, useContext, useState, useEffect, useCallback } from "react";
import { users } from "@/lib/api";
import type { BusinessMembership } from "@/Types";
import { useAuth } from "./AuthProvider";

interface BusinessContextType {
  businesses: BusinessMembership[];
  activeBusiness: BusinessMembership | null;
  loadingBusinesses: boolean;
  switchBusiness: (b: BusinessMembership) => void;
  refreshBusinesses: () => Promise<void>;
}

const BusinessContext = createContext<BusinessContextType | null>(null);

export function BusinessProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [businesses, setBusinesses] = useState<BusinessMembership[]>([]);
  const [activeBusiness, setActiveBusiness] = useState<BusinessMembership | null>(null);
  const [loadingBusinesses, setLoadingBusinesses] = useState(false);

  const fetchBusinesses = useCallback(async () => {
    setLoadingBusinesses(true);
    try {
      const list = await users.businesses();
      setBusinesses(list);
      setActiveBusiness((prev) => {
        if (prev) return prev;
        const stored = typeof window !== "undefined"
          ? localStorage.getItem("activeBusinessId") : null;
        return (stored ? list.find((b) => String(b.businessId) === stored) : null) ?? list[0] ?? null;
      });
    } catch {}
    setLoadingBusinesses(false);
  }, []);

  useEffect(() => {
    if (user) fetchBusinesses();
    else {
      setBusinesses([]);
      setActiveBusiness(null);
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
      businesses, activeBusiness, loadingBusinesses,
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
