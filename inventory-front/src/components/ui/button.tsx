import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";
import { forwardRef } from "react";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-xl text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand focus-visible:ring-offset-2 focus-visible:ring-offset-frame disabled:pointer-events-none disabled:opacity-50",
  {
    variants: {
      variant: {
        default:     "bg-brand-strong text-white hover:bg-brand-deep shadow-[var(--shadow-brand)]",
        accent:      "bg-accent text-on-accent hover:bg-accent-deep shadow-[var(--shadow-accent)]",
        destructive: "bg-red-500 text-white hover:bg-red-600 shadow-sm",
        outline:     "border border-line bg-surface text-ink-2 hover:bg-surface-2 hover:text-ink shadow-card",
        secondary:   "bg-surface-2 text-ink hover:bg-gray-200",
        ghost:       "text-ink-2 hover:bg-surface-2 hover:text-ink",
        link:        "text-blue-600 underline-offset-4 hover:underline",
      },
      size: {
        default: "h-10 px-4 py-2",
        sm:      "h-9 rounded-xl px-3 text-xs",
        lg:      "h-12 rounded-2xl px-8",
        icon:    "h-10 w-10 rounded-xl",
      },
    },
    defaultVariants: { variant: "default", size: "default" },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, ...props }, ref) => (
    <button
      className={cn(buttonVariants({ variant, size, className }))}
      ref={ref}
      {...props}
    />
  )
);
Button.displayName = "Button";

export { Button, buttonVariants };
