import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { APP_NAME } from "@/lib/brand";
import { LogoMark } from "@/components/layout/Logo";
import { Slide, CountUp } from "@/components/landing/Slide";
import { HeroBox, LayerStack, MODULES } from "@/components/landing/Drawings";
import { FeatureIndex } from "@/components/landing/FeatureIndex";

/**
 * The public landing page (signed-in people are sent to the app by proxy.ts). Told as a
 * presentation deck: cover → the problem → one system → process → feature index → numbers → start.
 */

const d = (s: number) => ({ ["--d" as string]: `${s}s` });
const YEAR = new Date().getFullYear();

function Corners() {
  return (
    <>
      <span className="lp-corner left-4 top-4 border-l border-t" />
      <span className="lp-corner right-4 top-4 border-r border-t" />
      <span className="lp-corner bottom-4 left-4 border-b border-l" />
      <span className="lp-corner bottom-4 right-4 border-b border-r" />
    </>
  );
}

/** "LABEL ● LABEL ● LABEL" row between hairlines, like a deck's meta line. */
function MetaRow({ items, className }: { items: string[]; className?: string }) {
  return (
    <div className={`lp-label flex items-center justify-between gap-3 ${className ?? ""}`}>
      {items.map((t, i) => (
        <span key={t} className="flex items-center gap-3">
          {i > 0 && <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />}
          {t}
        </span>
      ))}
    </div>
  );
}

function SlideHeader({ n, title, right }: { n: string; title: string; right?: string }) {
  return (
    <div className="lp-rise flex items-center justify-between gap-4 border-b border-current/20 pb-4">
      <p className="lp-label flex items-center gap-2.5"><span className="lp-tag">{n}</span>{APP_NAME} / {title}<span aria-hidden="true" className="h-2 w-2 bg-current" /></p>
      {right && <p className="lp-label hidden opacity-70 sm:block">[ {right} ]</p>}
    </div>
  );
}

export default function LandingPage() {
  return (
    <div className="lp min-h-dvh bg-[var(--lp-ink)]">
      {/* ── 00 Cover ─────────────────────────────────────────────────── */}
      <Slide tone="dark" className="min-h-dvh">
        <Corners />
        <div className="mx-auto flex min-h-dvh max-w-[1400px] flex-col px-6 sm:px-10">
          <header className="flex items-center justify-between gap-4 py-6">
            <Link href="/" className="flex items-center gap-2.5">
              <span className="flex h-8 w-8 items-center justify-center bg-white"><LogoMark className="h-6 w-6" /></span>
              <span className="lp-label text-[12px] text-white">{APP_NAME}</span>
            </Link>
            <nav className="lp-nav flex items-center gap-2 sm:gap-3">
              <Link href="/login" className="lp-btn h-9 border-white/0 px-3 text-white/80 hover:text-white">Sign in</Link>
              <Link href="/register" className="lp-btn lp-btn-solid h-9 px-3">Start free</Link>
            </nav>
          </header>

          <div className="grid flex-1 items-end gap-10 pb-12 lg:grid-cols-2 lg:gap-0 lg:pb-16">
            <div className="order-2 lg:order-1 lg:pr-12">
              <div className="lp-rise border-y border-white/20 py-2.5" style={d(0.1)}>
                <MetaRow items={[`${APP_NAME}`, "Stock · Sales · Books", String(YEAR)]} />
                <MetaRow className="mt-1 text-[9px] text-white/50" items={["For shops with branches", "One account, many companies"]} />
              </div>
              <h1 className="lp-rise lp-headline mt-6 text-[2.6rem] sm:text-6xl xl:text-7xl" style={d(0.25)}>
                Every item<br />accounted for
              </h1>
              <p className="lp-rise mt-6 max-w-md text-[13px] uppercase leading-relaxed tracking-[.06em] text-white/70" style={d(0.4)}>
                Point of sale, stock across every branch, and books that balance themselves. One system, from the shelf to the balance sheet.
              </p>
              <div className="lp-rise mt-8 flex flex-wrap gap-3" style={d(0.55)}>
                <Link href="/register" className="lp-btn lp-btn-solid">Create your company <ArrowUpRight className="h-4 w-4" /></Link>
                <Link href="#problem" className="lp-btn text-white">See how it works</Link>
              </div>
              <div className="lp-rise mt-8 border-y border-white/20 py-2.5" style={d(0.7)}>
                <div className="lp-label flex items-center justify-between gap-3 text-white/80">
                  <span>Status · Live</span>
                  <span className="flex items-center gap-2"><span className="lp-live" />Sales posting now</span>
                  <span className="hidden sm:inline">GHS</span>
                </div>
              </div>
            </div>

            <div className="relative order-1 lg:order-2 lg:h-full lg:border-l lg:border-white/15 lg:pl-12">
              <div className="flex h-full items-center justify-center">
                <HeroBox className="w-full max-w-[520px] text-white" />
              </div>
              {/* vertical rail, like the deck's edge labels */}
              <div className="lp-label absolute -right-2 top-0 hidden h-full flex-col items-center justify-between py-4 text-[10px] text-white/50 [writing-mode:vertical-rl] xl:flex">
                <span>{APP_NAME}</span><span>[ Rev A ]</span><span>[ Drawn 1:10 ]</span>
              </div>
            </div>
          </div>
        </div>
      </Slide>

      {/* ── 01 The problem ───────────────────────────────────────────── */}
      <Slide tone="light" id="problem">
        <Corners />
        <div className="mx-auto max-w-[1400px] px-6 py-16 sm:px-10 sm:py-24">
          <SlideHeader n="01" title="The problem" right="Before" />
          <h2 className="lp-rise lp-headline mt-10 max-w-4xl text-3xl sm:text-5xl" style={d(0.1)}>Stock in a notebook. Sales in the till. Books somewhere else.</h2>

          <div className="mt-14 grid items-center gap-6 lg:grid-cols-[1fr_auto_1fr]">
            <div className="flex flex-col gap-4">
              {[
                ["Notebook", "What's on the shelf"],
                ["Till roll", "What was sold"],
                ["Spreadsheet", "What it all means"],
              ].map(([a, b], i) => (
                <div key={a} className="lp-rise flex items-center gap-4" style={d(0.2 + i * 0.12)}>
                  <div className="flex-1 border border-[var(--lp-ink)]/40 bg-[var(--lp-paper)] px-4 py-3">
                    <p className="lp-label text-[12px]">{a}</p>
                    <p className="lp-label mt-1 text-[10px] opacity-60">{b}</p>
                  </div>
                  <span className="lp-dash-x hidden w-16 lg:block" />
                </div>
              ))}
            </div>
            <div className="lp-rise flex justify-center" style={d(0.6)}>
              <span className="lp-dash-y h-10 lg:hidden" />
            </div>
            <div className="lp-rise border border-[var(--lp-ink)] bg-[var(--lp-ink)] px-6 py-8 text-white" style={d(0.7)}>
              <p className="lp-label text-white/60">[ Result ]</p>
              <p className="lp-headline mt-3 text-2xl sm:text-3xl">You, at closing time, adding it all up</p>
              <p className="mt-4 text-[12px] uppercase leading-relaxed tracking-[.06em] text-white/70">
                Counts that don&apos;t match, credit you forgot, and no idea which branch actually made money.
              </p>
            </div>
          </div>

          <div className="lp-rise mt-14" style={d(0.8)}>
            <p className="lp-label flex gap-3"><span>Progress</span><span>&gt;&gt;&gt;</span><span>Nothing adds up</span></p>
            <div className="lp-progress mt-2 h-1 bg-[var(--lp-ink)]/15" style={{ ["--w" as string]: "28%" }}><span /></div>
          </div>
        </div>
      </Slide>

      {/* ── 02 One system ────────────────────────────────────────────── */}
      <Slide tone="dark">
        <Corners />
        <div className="mx-auto max-w-[1400px] px-6 py-16 sm:px-10 sm:py-24">
          <SlideHeader n="02" title="One system" right="Six parts, one record" />
          <div className="mt-10 grid items-center gap-10 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
            <div>
              <h2 className="lp-rise lp-headline text-3xl sm:text-5xl" style={d(0.1)}>Built as<br />one system</h2>
              <LayerStack className="mt-8 w-full max-w-[640px] text-white" />
            </div>
            <div className="lp-rise space-y-8" style={d(0.4)}>
              <div>
                <p className="lp-label text-white/60">Three things it does</p>
                <ul className="mt-4 space-y-3 text-[13px] uppercase tracking-[.08em]">
                  {["Counts every item, in every place", "Records every sale and payment once", "Keeps the books balanced, automatically"].map((t) => (
                    <li key={t} className="flex items-start gap-3 border-b border-white/15 pb-3"><ArrowUpRight className="mt-0.5 h-4 w-4 shrink-0 text-brand" />{t}</li>
                  ))}
                </ul>
              </div>
              <ol className="grid grid-cols-2 gap-x-6 gap-y-2">
                {MODULES.map((m) => (
                  <li key={m.n} className="lp-label flex items-center gap-2.5 text-[10px] text-white/75"><span className="lp-tag">{m.n}</span>{m.name}</li>
                ))}
              </ol>
              <p className="lp-label border-t border-white/15 pt-4 text-white/60">Stock / Sales / Books</p>
            </div>
          </div>
        </div>
      </Slide>

      {/* ── 03 Process // 3 stages ───────────────────────────────────── */}
      <Slide tone="light">
        <Corners />
        <div className="mx-auto max-w-[1400px] px-6 py-16 sm:px-10 sm:py-24">
          <SlideHeader n="03" title="Process" right="All stages aligned" />
          <div className="mt-12 grid items-center gap-8 lg:grid-cols-[1fr_auto_1fr]">
            <div className="flex flex-col gap-4 lg:items-end">
              {["Excel upload", "Invite by @username"].map((t, i) => (
                <div key={t} className="lp-rise flex items-center gap-3" style={d(0.2 + i * 0.1)}>
                  <span className="lp-label border border-[var(--lp-ink)]/50 bg-[var(--lp-paper)] px-4 py-2.5 text-[11px]">{t}</span>
                  <span className="lp-dash-x hidden w-14 lg:block" />
                </div>
              ))}
            </div>

            <div className="lp-rise mx-auto w-full max-w-md border border-[var(--lp-ink)]/50 bg-[var(--lp-paper)]" style={d(0.35)}>
              <p className="lp-headline bg-[var(--lp-ink)] px-4 py-2.5 text-center text-lg text-white">Process // 3 stages</p>
              <div className="flex flex-col items-center gap-0 p-5">
                {[
                  ["01", "Set up", "Products, branches and people, from a spreadsheet"],
                  ["02", "Run", "Sell, receive and move stock every day"],
                  ["03", "Report", "Profit & loss and balance sheet, any time"],
                ].map(([n, a, b], i) => (
                  <div key={n} className="flex w-full flex-col items-center">
                    {i > 0 && <span className="lp-dash-y h-5" />}
                    <div className="w-full border border-[var(--lp-ink)]/50 px-4 py-3 text-center">
                      <p className="lp-label flex items-center justify-center gap-2 text-[12px]"><span className="lp-tag">{n}</span>{a}</p>
                      <p className="lp-label mt-1.5 text-[10px] opacity-60">{b}</p>
                    </div>
                  </div>
                ))}
                <p className="lp-label mt-4 text-[11px]">— Books always balanced —</p>
              </div>
            </div>

            <div className="flex flex-col gap-4">
              {["Daily sales", "Low-stock alerts"].map((t, i) => (
                <div key={t} className="lp-rise flex items-center gap-3" style={d(0.5 + i * 0.1)}>
                  <span className="lp-dash-x hidden w-14 lg:block" />
                  <span className="lp-label border border-[var(--lp-ink)]/50 bg-[var(--lp-paper)] px-4 py-2.5 text-[11px]">{t}</span>
                </div>
              ))}
            </div>
          </div>
          <p className="lp-rise lp-label mt-10 text-center text-[11px]" style={d(0.7)}>Low stock becomes the next purchase</p>

          <div className="lp-rise mt-12" style={d(0.8)}>
            <p className="lp-label flex gap-3"><span>Progress</span><span>&gt;&gt;&gt;</span><span>Systems in place</span></p>
            <div className="lp-progress mt-2 h-1 bg-[var(--lp-ink)]/15" style={{ ["--w" as string]: "90%" }}><span /></div>
          </div>
        </div>
      </Slide>

      {/* ── 04 Feature index ─────────────────────────────────────────── */}
      <Slide tone="dark">
        <div className="mx-auto max-w-[1400px] px-6 py-16 sm:px-10 sm:py-24">
          <div className="lp-rise lp-label mb-10 grid grid-cols-[auto_1fr_auto] border border-white/25">
            <span className="border-r border-white/25 px-4 py-2.5">{APP_NAME}</span>
            <span />
            <span className="border-l border-white/25 px-4 py-2.5">Feature index</span>
          </div>
          <FeatureIndex />
        </div>
      </Slide>

      {/* ── 05 By the numbers ────────────────────────────────────────── */}
      <Slide tone="light">
        <Corners />
        <div className="mx-auto max-w-[1400px] px-6 py-16 sm:px-10 sm:py-24">
          <SlideHeader n="05" title="By the numbers" right="As built" />
          <div className="mt-12 grid grid-cols-2 border-l border-t border-[var(--lp-ink)]/25 lg:grid-cols-4">
            {[
              [1, "Sign-in for every company you work in"],
              [6, "Roles, from owner to driver"],
              [9, "Spreadsheet uploads, all or nothing"],
              [13, "Colour themes for your company"],
            ].map(([n, label], i) => (
              <div key={label} className="lp-rise border-b border-r border-[var(--lp-ink)]/25 bg-[var(--lp-paper)]/70 p-5 sm:p-7" style={d(0.15 + i * 0.12)}>
                <p className="lp-headline text-5xl sm:text-7xl"><CountUp to={n as number} /></p>
                <p className="lp-label mt-4 max-w-[14rem] text-[11px] leading-relaxed">{label}</p>
              </div>
            ))}
          </div>
        </div>
      </Slide>

      {/* ── 06 Start ─────────────────────────────────────────────────── */}
      <Slide tone="blue">
        <div className="mx-auto grid max-w-[1400px] lg:grid-cols-[1.3fr_1fr_1fr]">
          <div className="border-white/20 px-6 py-16 sm:px-10 lg:border-r lg:py-24">
            <p className="lp-rise lp-label flex items-center gap-2.5"><span className="lp-tag lp-tag-light">06</span>{APP_NAME} / Start<span aria-hidden="true" className="h-2 w-2 bg-white" /></p>
            <h2 className="lp-rise lp-headline mt-10 text-4xl sm:text-6xl" style={d(0.15)}>Run the<br />next branch<br />with us</h2>
          </div>
          <div className="border-t border-white/20 px-6 py-10 sm:px-10 lg:border-r lg:border-t-0 lg:py-24">
            <ol className="lp-rise space-y-3" style={d(0.3)}>
              {["Create your account", "Create your company", "Upload your products", "Invite your team", "Make the first sale"].map((t, i) => (
                <li key={t} className="lp-label flex items-center gap-3 text-[11px]"><span className="lp-tag lp-tag-light">{String(i + 1).padStart(2, "0")}</span>{t}</li>
              ))}
            </ol>
          </div>
          <div className="flex flex-col justify-between gap-10 border-t border-white/20 px-6 py-10 sm:px-10 lg:border-t-0 lg:py-24">
            <p className="lp-rise text-[13px] uppercase leading-relaxed tracking-[.06em] text-white/85" style={d(0.45)}>
              Free to start. Create your company in a minute and bring your stock in from a spreadsheet.
            </p>
            <div className="lp-rise flex flex-col gap-3" style={d(0.6)}>
              <Link href="/register" className="lp-btn justify-between bg-white text-brand-deep hover:bg-white/90">Create an account <ArrowUpRight className="h-4 w-4" /></Link>
              <Link href="/login" className="lp-btn justify-between text-white hover:bg-white hover:text-brand-deep">Sign in <ArrowUpRight className="h-4 w-4" /></Link>
            </div>
          </div>
        </div>
        <footer className="lp-label border-t border-white/20 px-6 py-5 text-[10px] text-white/60 sm:px-10">
          <div className="mx-auto flex max-w-[1400px] justify-between gap-4"><span>© {YEAR} {APP_NAME}</span><span>Stock / Sales / Books</span></div>
        </footer>
      </Slide>
    </div>
  );
}
