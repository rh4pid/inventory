import { BarChart3, Boxes, Users } from "lucide-react";
import { APP_NAME, APP_TAGLINE } from "@/lib/brand";
import { LogoMark } from "@/components/layout/Logo";
import { StoreScene } from "./StoreScene";

const POINTS = [
  { icon: Boxes, text: "Stock across every branch and van" },
  { icon: BarChart3, text: "Sales, purchases and the books, kept in step" },
  { icon: Users, text: "Your team joins by @username" },
];

/**
 * Sign-in and sign-up pages. Large screens: the animated store on a blue panel (left) beside the
 * form. Phones and tablets: the blue panel sits on top with a smaller scene, and the form slides
 * up over it as a rounded sheet. The form is ready straight away at every size.
 */
export function AuthShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-frame lg:flex-row">
      <section className="relative flex shrink-0 flex-col overflow-hidden bg-brand-strong px-5 pb-10 pt-5 text-white sm:px-8 sm:pb-12 lg:w-[52%] lg:max-w-[760px] lg:justify-between lg:px-10 lg:py-9 xl:px-14">
        {/* soft light behind the scene */}
        <div aria-hidden="true" className="pointer-events-none absolute -right-24 top-1/4 h-[22rem] w-[22rem] rounded-full bg-white/10 blur-3xl lg:h-[28rem] lg:w-[28rem]" />

        <div className="relative flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/95 lg:h-10 lg:w-10"><LogoMark className="h-6 w-6 lg:h-7 lg:w-7" /></span>
          <span className="text-lg font-semibold tracking-tight lg:text-xl">{APP_NAME}</span>
        </div>

        <div className="relative mx-auto w-full max-w-[400px] pt-4 sm:max-w-[500px] lg:max-w-none lg:py-6">
          <StoreScene />
        </div>

        <div className="relative hidden space-y-4 lg:block">
          <p className="max-w-md text-2xl font-semibold leading-snug tracking-tight">{APP_TAGLINE}</p>
          <ul className="space-y-2 text-sm text-white/85">
            {POINTS.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-2.5"><Icon className="h-4 w-4 shrink-0 text-white/70" />{text}</li>
            ))}
          </ul>
        </div>
      </section>

      {/* On small screens the form is a sheet that overlaps the bottom of the blue panel. */}
      <main className="relative z-10 -mt-6 flex flex-1 justify-center rounded-t-[1.75rem] bg-frame px-5 pb-10 pt-8 sm:px-8 sm:pt-10 lg:mt-0 lg:items-center lg:rounded-none lg:py-8">
        <div className="ls-form w-full max-w-md">{children}</div>
      </main>
    </div>
  );
}
