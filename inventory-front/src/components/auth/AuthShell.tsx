import { APP_NAME } from "@/lib/brand";
import { LogoMark } from "@/components/layout/Logo";
import { BlueprintScene } from "./BlueprintScene";

/**
 * Sign-in and sign-up pages.
 * - Laptops and up: the animated blueprint on a blue panel (left) beside the form (right).
 * - Phones and tablets: the blueprint fills the screen and the form sits below the drawing
 *   on a plain white card.
 * The form is ready straight away at every size.
 */
export function AuthShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="bp-sheet-sm relative flex min-h-dvh flex-col lg:flex-row lg:bg-frame">
      {/* drawing-sheet frame around the whole page on small screens */}
      <div aria-hidden="true" className="pointer-events-none fixed inset-2.5 z-0 border border-white/30 sm:inset-3.5 lg:hidden" />

      <section className="bp-sheet-lg relative flex shrink-0 flex-col overflow-hidden px-6 pb-4 pt-6 text-white sm:px-9 lg:w-[54%] lg:max-w-[800px] lg:justify-center lg:gap-8 lg:px-12 lg:py-10 xl:px-16">
        {/* drawing-sheet frame around the panel on laptops */}
        <div aria-hidden="true" className="pointer-events-none absolute inset-3.5 hidden border border-white/30 lg:block" />

        <div className="relative flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/95 lg:h-10 lg:w-10"><LogoMark className="h-6 w-6 lg:h-7 lg:w-7" /></span>
          <span className="text-lg font-semibold tracking-tight lg:text-xl">{APP_NAME}</span>
        </div>

        <div className="relative mx-auto w-full max-w-[420px] pt-4 sm:max-w-[620px] lg:max-w-none lg:pt-0">
          <BlueprintScene />
        </div>
      </section>

      <main className="relative z-10 flex flex-1 justify-center px-4 pb-8 pt-2 sm:px-8 sm:pb-12 lg:items-center lg:py-8">
        <div
          className={
            "ls-form w-full max-w-md self-start rounded-2xl border border-line bg-surface p-6 " +
            "shadow-[0_1px_2px_rgb(16_24_40/0.06),0_12px_32px_-16px_rgb(16_24_40/0.35)] sm:p-8 " +
            "lg:self-auto lg:rounded-none lg:border-0 lg:bg-transparent lg:p-0 lg:shadow-none"
          }
        >
          {children}
        </div>
      </main>
    </div>
  );
}
