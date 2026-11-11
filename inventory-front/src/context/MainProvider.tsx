"use client";
import { AuthProvider } from "./AuthProvider";
import { BusinessProvider } from "./BusinessContext";
import { Toaster } from "sonner";

export function MainProvider({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <BusinessProvider>
        {children}
        <Toaster richColors position="top-right" />
      </BusinessProvider>
    </AuthProvider>
  );
}
