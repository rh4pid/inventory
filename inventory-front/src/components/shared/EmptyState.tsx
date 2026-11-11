import { cn } from "@/lib/utils";
import { PackageOpen } from "lucide-react";

interface Props {
  title?: string;
  description?: string;
  action?: React.ReactNode;
  icon?: React.ReactNode;
  className?: string;
}

export function EmptyState({
  title = "No results",
  description = "Nothing here yet.",
  action,
  icon,
  className,
}: Props) {
  return (
    <div className={cn("flex flex-col items-center justify-center py-16 text-center", className)}>
      <div className="mb-4 text-gray-300">
        {icon ?? <PackageOpen className="h-12 w-12" />}
      </div>
      <h3 className="text-base font-semibold text-gray-700">{title}</h3>
      <p className="mt-1 text-sm text-gray-500 max-w-xs">{description}</p>
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
