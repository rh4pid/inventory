import { cn } from "@/lib/utils";

interface Props { className?: string; size?: "sm" | "md" | "lg"; }

export function LoadingSpinner({ className, size = "md" }: Props) {
  const sz = { sm: "h-4 w-4 border-2", md: "h-8 w-8 border-2", lg: "h-12 w-12 border-4" }[size];
  return (
    <div
      className={cn(
        "animate-spin rounded-full border-gray-300 border-t-blue-600",
        sz, className
      )}
    />
  );
}

export function PageLoader() {
  return (
    <div className="flex h-full min-h-[400px] items-center justify-center">
      <LoadingSpinner size="lg" />
    </div>
  );
}
