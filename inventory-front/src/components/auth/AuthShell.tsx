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
 * Sign-in and sign-up pages: the animated store on a blue panel (left, large screens) and the
 * form on the right, ready straight away. Small screens show a short blue header instead.
 */
export function AuthShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh bg-frame">
      <section className="relative hidden w-[52%] max-w-[760px] shrink-0 flex-col justify-between overflow-hidden bg-brand-strong px-10 py-9 text-white lg:flex xl:px-14">
        {/* soft light behind the scene */}
        <div aria-hidden="true" className="pointer-events-none absolute -right-24 top-1/4 h-[28rem] w-[28rem] rounded-full bg-white/10 blur-3xl" />
        <div className="relative flex items-center gap-2.5">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/95"><LogoMark className="h-7 w-7" /></span>
          <span className="text-xl font-semibold tracking-tight">{APP_NAME}</span>
        </div>

        <div className="relative py-6">
          <StoreScene />
        </div>

        <div className="relative space-y-4">
          <p className="max-w-md text-2xl font-semibold leading-snug tracking-tight">{APP_TAGLINE}</p>
          <ul className="space-y-2 text-sm text-white/85">
            {POINTS.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-2.5"><Icon className="h-4 w-4 shrink-0 text-white/70" />{text}</li>
            ))}
          </ul>
        </div>
      </section>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-2.5 bg-brand-strong px-5 py-4 text-white lg:hidden">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-white/95"><LogoMark className="h-6 w-6" /></span>
          <span className="text-lg font-semibold tracking-tight">{APP_NAME}</span>
        </header>
        <main className="flex flex-1 items-center justify-center px-4 py-8 sm:px-8">
          <div className="ls-form w-full max-w-md">{children}</div>
        </main>
      </div>
    </div>
  );
}
