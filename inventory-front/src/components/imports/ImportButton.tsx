"use client";
import { useRef, useState } from "react";
import Link from "next/link";
import { AlertTriangle, CheckCircle2, Download, FileSpreadsheet, Loader2, Upload } from "lucide-react";
import { toast } from "sonner";
import { imports, saveBlob } from "@/lib/api";
import { errorMessage } from "@/lib/utils";
import { useBusiness } from "@/context/BusinessContext";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import type { ImportKind, ImportResult } from "@/Types";

const LABEL: Record<ImportKind, string> = {
  products: "products", categories: "categories", "product-features": "product features", locations: "locations", people: "team members", customers: "customers",
  suppliers: "suppliers", accounts: "accounts", employees: "payroll employees",
};

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

/** Builds a CSV of the sign-ins a team upload created (shown once, so people can save them). */
function credentialsCsv(r: ImportResult) {
  const esc = (v: string | undefined) => `"${(v ?? "").replace(/"/g, '""')}"`;
  const lines = [["Name", "Email", "Username", "Temporary password"].map(esc).join(",")];
  r.credentials.forEach((c) => lines.push([c.name, c.email, c.username ? `@${c.username}` : "", c.temporaryPassword].map(esc).join(",")));
  return new Blob([lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
}

/**
 * "Upload" button + dialog: download the template, choose the filled-in file, see every problem
 * (nothing is saved until the whole file is fine), then save it.
 */
export function ImportButton({ kind, onDone, label }: { kind: ImportKind; onDone?: () => void; label?: string }) {
  const { activeBusiness } = useBusiness();
  const bizId = activeBusiness?.businessId;
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [checked, setChecked] = useState<ImportResult | null>(null);
  const [saved, setSaved] = useState<ImportResult | null>(null);
  const [busy, setBusy] = useState<"template" | "check" | "save" | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const what = LABEL[kind];

  const reset = () => { setFile(null); setChecked(null); setSaved(null); if (input.current) input.current.value = ""; };
  const close = () => { setOpen(false); if (saved?.saved) onDone?.(); reset(); };

  const template = async () => {
    if (!bizId) return;
    setBusy("template");
    try { await imports.template(bizId, kind); }
    catch (err) { toast.error(errorMessage(err, "Could not download the template")); }
    finally { setBusy(null); }
  };

  const check = async (f: File) => {
    if (!bizId) return;
    setFile(f); setChecked(null); setSaved(null);
    setBusy("check");
    try { setChecked(await imports.upload(bizId, kind, f, true)); }
    catch (err) { toast.error(errorMessage(err, "Could not check the file")); }
    finally { setBusy(null); }
  };

  const save = async () => {
    if (!bizId || !file) return;
    setBusy("save");
    try {
      const r = await imports.upload(bizId, kind, file, false);
      if (r.saved) { setSaved(r); toast.success(`Uploaded ${plural(r.rows, "row")}`); }
      else setChecked(r); // something failed while saving: show why (nothing was saved)
    } catch (err) { toast.error(errorMessage(err, "Could not upload the file")); }
    finally { setBusy(null); }
  };

  if (!bizId) return null;

  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}><Upload className="h-4 w-4" /> {label ?? "Upload"}</Button>
      <Dialog open={open} onOpenChange={(o) => !o && close()}>
        <DialogContent className="max-w-2xl">
          <DialogHeader><DialogTitle>Upload {what}</DialogTitle></DialogHeader>

          {saved ? (
            <div className="space-y-4">
              <div className="flex items-start gap-3 rounded-2xl bg-brand-soft px-4 py-3 text-sm text-brand-deep">
                <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" />
                <div>
                  <b>Done.</b>{" "}
                  {[saved.created && `${saved.created} added`, saved.updated && `${saved.updated} updated`, saved.invited && `${saved.invited} invited`]
                    .filter(Boolean).join(", ") || "Nothing to change"}.
                  {saved.invited > 0 && " Invited people join when they accept."}
                </div>
              </div>
              {saved.credentials.length > 0 && (
                <div className="space-y-2">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm font-medium text-ink">New sign-ins: save these now, they won&apos;t be shown again.</p>
                    <Button size="sm" variant="outline" onClick={() => saveBlob(credentialsCsv(saved), "new-sign-ins.csv")}>
                      <Download className="h-4 w-4" /> Download (CSV)
                    </Button>
                  </div>
                  <div className="max-h-64 overflow-auto rounded-2xl border border-line">
                    <table className="w-full text-left text-sm">
                      <thead className="sticky top-0 bg-surface-2 text-xs text-ink-3">
                        <tr><th className="px-3 py-2">Name</th><th className="px-3 py-2">Username</th><th className="px-3 py-2">Email</th><th className="px-3 py-2">Temporary password</th></tr>
                      </thead>
                      <tbody className="divide-y divide-line/70">
                        {saved.credentials.map((c) => (
                          <tr key={c.email}>
                            <td className="px-3 py-2">{c.name}</td>
                            <td className="px-3 py-2 text-ink-3">{c.username ? `@${c.username}` : ""}</td>
                            <td className="px-3 py-2 text-ink-3">{c.email}</td>
                            <td className="px-3 py-2 font-mono">{c.temporaryPassword}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
              <DialogFooter><Button onClick={close}>Done</Button></DialogFooter>
            </div>
          ) : (
            <div className="space-y-4">
              <ol className="space-y-3 text-sm">
                <li className="flex flex-wrap items-center justify-between gap-2 rounded-2xl bg-surface-2 px-4 py-3">
                  <span><b>1.</b> Download the template and fill it in (the Instructions sheet explains each column).{" "}
                    <Link href={`/templates?kind=${kind}`} className="font-medium text-brand-deep hover:underline" onClick={close}>Preview it first</Link>
                  </span>
                  <Button size="sm" variant="outline" onClick={template} disabled={busy === "template"}>
                    {busy === "template" ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileSpreadsheet className="h-4 w-4" />} Template
                  </Button>
                </li>
                <li className="flex flex-wrap items-center justify-between gap-2 rounded-2xl bg-surface-2 px-4 py-3">
                  <span className="min-w-0"><b>2.</b> Choose your filled-in file. {file && <span className="text-ink-3">({file.name})</span>}</span>
                  <input ref={input} type="file" accept=".xlsx,.xls" className="sr-only" id={`import-${kind}`}
                    onChange={(e) => { const f = e.target.files?.[0]; if (f) void check(f); }} />
                  <Button size="sm" variant="outline" onClick={() => input.current?.click()} disabled={busy === "check"}>
                    {busy === "check" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />} {file ? "Choose again" : "Choose file"}
                  </Button>
                </li>
              </ol>

              {busy === "check" && <p className="flex items-center gap-2 text-sm text-ink-3"><Loader2 className="h-4 w-4 animate-spin" /> Checking every row…</p>}

              {checked && !checked.ok && (
                <div className="space-y-2" role="alert">
                  <p className="flex items-center gap-2 text-sm font-medium text-red-700">
                    <AlertTriangle className="h-4 w-4" />
                    {plural(checked.errors.length, "problem")} found. Nothing has been saved: fix them in your file and choose it again.
                  </p>
                  <div className="max-h-72 overflow-auto rounded-2xl border border-red-200">
                    <table className="w-full text-left text-sm">
                      <thead className="sticky top-0 bg-red-50 text-xs text-red-800">
                        <tr><th className="px-3 py-2">Row</th><th className="px-3 py-2">Column</th><th className="px-3 py-2">Problem</th></tr>
                      </thead>
                      <tbody className="divide-y divide-red-100">
                        {checked.errors.map((e, i) => (
                          <tr key={i}>
                            <td className="px-3 py-2 font-mono text-ink-2">{e.row > 0 ? e.row : "File"}</td>
                            <td className="px-3 py-2 text-ink-2">{e.column ?? ""}</td>
                            <td className="px-3 py-2 text-ink">{e.message}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}

              {checked?.ok && (
                <div className="space-y-2">
                  <p className="flex items-center gap-2 text-sm font-medium text-brand-deep">
                    <CheckCircle2 className="h-4 w-4" /> All {plural(checked.rows, "row")} look good.
                  </p>
                  {checked.notes.map((n) => <p key={n} className="text-xs text-ink-3">{n}</p>)}
                </div>
              )}

              <DialogFooter>
                <Button variant="outline" onClick={close}>Cancel</Button>
                <Button onClick={save} disabled={!checked?.ok || busy !== null}>
                  {busy === "save" ? "Uploading…" : checked?.ok ? `Upload ${plural(checked.rows, "row")}` : "Upload"}
                </Button>
              </DialogFooter>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
