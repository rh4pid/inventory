"use client";
import { cn } from "@/lib/utils";
import { businessLogoUrl } from "@/lib/api";
import { useBusiness } from "@/context/BusinessContext";
import { LogoMark } from "./Logo";

/**
 * The active company's logo if it has uploaded one, otherwise the product mark.
 * Used in the app frame (rail, header, mobile menu).
 */
export function CompanyLogo({ className }: { className?: string }) {
  const { activeBusiness } = useBusiness();
  const src = businessLogoUrl(activeBusiness?.businessLogo);
  if (!src) return <LogoMark className={className} />;
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt={`${activeBusiness?.businessName ?? "Company"} logo`}
      className={cn("h-9 w-9 rounded-xl bg-white object-contain", className)} />
  );
}
