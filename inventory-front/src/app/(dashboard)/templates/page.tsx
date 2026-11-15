"use client";
import { useEffect, useMemo, useState } from "react";
import { Download, FileSpreadsheet, Info, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { imports } from "@/lib/api";
import { cn, errorMessage } from "@/lib/utils";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { EmptyState } from "@/components/shared/EmptyState";
import { ImportButton } from "@/components/imports/ImportButton";
import { Button } from "@/components/ui/button";
import type { ImportKind, TemplateInfo } from "@/Types";

/** Spreadsheet column letters: 0 -> A, 25 -> Z, 26 -> AA. */
const colLetter = (i: number): string => (i < 26 ? String.fromCharCode(65 + i) : colLetter(Math.floor(i / 26) - 1) + colLetter(i % 26));

/**
 * One place for every upload: pick a template, see exactly what the spreadsheet looks like
 * (columns, an example row, what goes in each column), download it, and upload it back.
 */
export default function TemplatesPage() {
  const { activeBusiness } = useBusiness();
  const bizId = activeBusiness?.businessId;
  const [list, setList] = useState<TemplateInfo[] | null>(null);
  const [selected, setSelected] = useState<ImportKind | null>(null);
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    if (!bizId) return;
    imports.catalog(bizId)
      .then((l) => {
        setList(l);
        const wanted = new URLSearchParams(window.location.search).get("kind");
        setSelected((l.find((t) => t.kind === wanted) ?? l[0])?.kind ?? null);
      })
      .catch((err) => { toast.error(errorMessage(err, "Could not load the templates")); setList([]); });
  }, [bizId]);

  const t = useMemo(() => list?.find((x) => x.kind === selected) ?? null, [list, selected]);

  const pick = (kind: ImportKind) => {
    setSelected(kind);
    window.history.replaceState(null, "", `?kind=${kind}`);
  };

  const download = async () => {
    if (!bizId || !t) return;
    setDownloading(true);
    try { await imports.template(bizId, t.kind); }
    catch (err) { toast.error(errorMessage(err, "Could not download the template")); }
    finally { setDownloading(false); }
  };

  if (list === null) return <PageLoader />;

  return (
    <div>
      <PageHeader title="Templates" description="Bulk upload from a spreadsheet: pick a template, check how it looks, download it, fill it in and upload it." />

      {list.length === 0 ? (
        <EmptyState title="No uploads for your role" description="Ask a manager or admin if you need to upload a spreadsheet." />
      ) : (
        <div className="grid gap-5 lg:grid-cols-[17rem_minmax(0,1fr)]">
          {/* Picker */}
          <nav aria-label="Templates" className="flex gap-2 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible lg:pb-0">
            {list.map((x) => (
              <button
                key={x.kind}
                type="button"
                onClick={() => pick(x.kind)}
                aria-current={x.kind === selected ? "true" : undefined}
                className={cn(
                  "flex min-w-[12rem] shrink-0 items-start gap-3 rounded-2xl border px-4 py-3 text-left transition-colors lg:min-w-0",
                  x.kind === selected ? "border-brand bg-brand-soft" : "border-line bg-surface hover:bg-surface-2"
                )}
              >
                <FileSpreadsheet className={cn("mt-0.5 h-5 w-5 shrink-0", x.kind === selected ? "text-brand-deep" : "text-ink-3")} />
                <span className="min-w-0">
                  <span className="block text-sm font-semibold text-ink">{x.label}</span>
                  <span className="block text-xs text-ink-3">{x.columns.length} columns</span>
                </span>
              </button>
            ))}
          </nav>

          {/* Preview */}
          {t && (
            <section className="min-w-0 space-y-5" aria-label={`${t.label} template`}>
              <div className="flex flex-wrap items-start justify-between gap-3 rounded-3xl border border-line/80 bg-surface p-5 shadow-card">
                <div className="min-w-0">
                  <h2 className="text-lg font-semibold text-ink">{t.label}</h2>
                  <p className="mt-0.5 text-sm text-ink-3">{t.description}</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button variant="outline" onClick={download} disabled={downloading}>
                    {downloading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />} Download template
                  </Button>
                  <ImportButton kind={t.kind} label={`Upload ${t.label.toLowerCase()}`} />
                </div>
              </div>

              {/* What the spreadsheet looks like */}
              <div className="rounded-3xl border border-line/80 bg-surface p-5 shadow-card">
                <h3 className="mb-1 text-sm font-semibold text-ink">What the spreadsheet looks like</h3>
                <p className="mb-3 text-xs text-ink-3">Row 1 holds the headings (keep them as they are). The example row shows the kind of value each column takes. Columns marked * must be filled in.</p>
                <div className="overflow-x-auto rounded-xl border border-line">
                  <table className="border-collapse text-xs">
                    <thead>
                      <tr className="bg-surface-2 text-ink-3">
                        <th className="w-10 border-b border-r border-line px-2 py-1 font-normal" />
                        {t.columns.map((c, i) => (
                          <th key={c.key} className="border-b border-r border-line px-3 py-1 text-center font-normal last:border-r-0">{colLetter(i)}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td className="border-b border-r border-line bg-surface-2 px-2 py-1.5 text-center text-ink-3">1</td>
                        {t.columns.map((c) => (
                          <td key={c.key} className="whitespace-nowrap border-b border-r border-line bg-brand-soft px-3 py-1.5 font-semibold text-brand-deep last:border-r-0">
                            {c.header}{c.required && " *"}
                          </td>
                        ))}
                      </tr>
                      <tr>
                        <td className="border-b border-r border-line bg-surface-2 px-2 py-1.5 text-center text-ink-3">2</td>
                        {t.columns.map((c) => (
                          <td key={c.key} className="whitespace-nowrap border-b border-r border-line px-3 py-1.5 text-ink-2 last:border-r-0">
                            {c.example || <span className="text-ink-3/60">…</span>}
                          </td>
                        ))}
                      </tr>
                      {[3, 4].map((n) => (
                        <tr key={n}>
                          <td className={cn("border-r border-line bg-surface-2 px-2 py-1.5 text-center text-ink-3", n === 3 && "border-b")}>{n}</td>
                          {t.columns.map((c) => <td key={c.key} className={cn("border-r border-line px-3 py-1.5 last:border-r-0", n === 3 && "border-b")}>&nbsp;</td>)}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Column guide */}
              <div className="rounded-3xl border border-line/80 bg-surface p-5 shadow-card">
                <h3 className="mb-3 text-sm font-semibold text-ink">What goes in each column</h3>
                <ul className="divide-y divide-line/70">
                  {t.columns.map((c, i) => (
                    <li key={c.key} className="grid gap-1 py-2.5 sm:grid-cols-[2.5rem_12rem_minmax(0,1fr)] sm:gap-3">
                      <span className="font-mono text-xs text-ink-3">{colLetter(i)}</span>
                      <span className="text-sm font-medium text-ink">
                        {c.header}{" "}
                        <span className={cn("ml-1 rounded-md px-1.5 py-0.5 text-[11px] font-medium", c.required ? "bg-accent-soft text-accent-deep" : "bg-surface-2 text-ink-3")}>
                          {c.required ? "Required" : "Optional"}
                        </span>
                      </span>
                      <span className="text-sm text-ink-2">
                        {c.help || <span className="text-ink-3">—</span>}
                        {c.options.length > 0 && (
                          <span className="mt-1 block text-xs text-ink-3">Choose from: {c.options.join(", ")}</span>
                        )}
                        {c.example && <span className="mt-0.5 block text-xs text-ink-3">e.g. {c.example}</span>}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>

              {/* Rules */}
              <div className="flex gap-3 rounded-3xl border border-line/80 bg-surface-2 p-5 text-sm text-ink-2">
                <Info className="mt-0.5 h-4 w-4 shrink-0 text-brand" />
                <ul className="space-y-1">
                  <li><b>All or nothing:</b> if any row has a problem, nothing is saved and every problem is listed by row and column.</li>
                  <li>You can check a file first: choosing it shows the problems before anything is saved.</li>
                  {t.notes.map((n) => <li key={n}>{n}</li>)}
                </ul>
              </div>
            </section>
          )}
        </div>
      )}
    </div>
  );
}
