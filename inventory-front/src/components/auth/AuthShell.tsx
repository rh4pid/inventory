import { APP_NAME } from "@/lib/brand";
import { LogoMark } from "@/components/layout/Logo";
import { BlueprintScene } from "./BlueprintScene";

/**
 * Sign-in and sign-up pages. Large screens: the animated blueprint on a blue panel (left) beside the
 * form. Phones and tablets: the blue panel sits on top with a smaller scene, and the form slides
 * up over it as a rounded sheet. The form is ready straight away at every size.
 */
export function AuthShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-frame lg:flex-row">
      <section className="bp-grid relative flex shrink-0 flex-col overflow-hidden bg-brand-deep px-6 pb-10 pt-6 text-white sm:px-9 sm:pb-12 lg:w-[54%] lg:max-w-[800px] lg:justify-center lg:gap-8 lg:px-12 lg:py-10 xl:px-16">
        {/* drawing-sheet frame, like a printed blueprint */}
        <div aria-hidden="true" className="pointer-events-none absolute inset-2.5 border border-white/30 sm:inset-3.5" />

        <div className="relative flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/95 lg:h-10 lg:w-10"><LogoMark className="h-6 w-6 lg:h-7 lg:w-7" /></span>
          <span className="text-lg font-semibold tracking-tight lg:text-xl">{APP_NAME}</span>
        </div>

        <div className="relative mx-auto w-full max-w-[420px] pt-4 sm:max-w-[620px] lg:max-w-none lg:pt-0">
          <BlueprintScene />
        </div>
      </section>

      {/* On small screens the form is a sheet that overlaps the bottom of the blue panel. */}
      <main className="relative z-10 -mt-6 flex flex-1 justify-center rounded-t-[1.75rem] bg-frame px-5 pb-10 pt-8 sm:px-8 sm:pt-10 lg:mt-0 lg:items-center lg:rounded-none lg:py-8">
        <div className="ls-form w-full max-w-md">{children}</div>
      </main>
    </div>
  );
}
