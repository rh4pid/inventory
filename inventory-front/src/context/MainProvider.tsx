"use client";
import { AuthProvider } from "./AuthProvider";
import { BusinessProvider } from "./BusinessContext";
import { Toaster } from "sonner";
import { ConfirmProvider } from "./ConfirmProvider";

export function MainProvider({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <BusinessProvider>
        <ConfirmProvider>{children}</ConfirmProvider>
        <Toaster richColors position="top-right" />
      </BusinessProvider>
    </AuthProvider>
  );
}
