"use client";
import { useEffect, useRef, useState } from "react";
import { ImagePlus, Loader2, Trash2 } from "lucide-react";
import { productImageUrl } from "@/lib/api";
import { shrinkImage } from "@/lib/images";
import { cn, errorMessage } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { toast } from "sonner";

/** A product's picture, or its initials on a tinted square when it has none. */
export function ProductThumb({ name, image, className }: { name: string; image?: string | null; className?: string }) {
  const src = productImageUrl(image);
  const [broken, setBroken] = useState(false);
  if (src && !broken) {
    // eslint-disable-next-line @next/next/no-img-element -- served by our API, already small
    return <img src={src} alt="" loading="lazy" onError={() => setBroken(true)}
      className={cn("rounded-xl bg-surface-2 object-cover", className)} />;
  }
  return (
    <div aria-hidden="true" className={cn("flex items-center justify-center rounded-xl bg-brand-soft text-xs font-bold text-brand-deep", className)}>
      {name.trim().slice(0, 2).toUpperCase()}
    </div>
  );
}

/**
 * Picture picker for the product form. Shows the current picture (or the one just chosen),
 * shrinks it in the browser, and hands the result back; the page uploads it on save.
 */
export function ProductImagePicker({ name, image, onPick, onRemove }: {
  name: string;
  /** The saved picture's key. */
  image?: string | null;
  /** The new picture (already shrunk), or null when cleared. */
  onPick: (blob: Blob | null) => void;
  /** Removing the saved picture (edit page). */
  onRemove?: () => Promise<void>;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  const choose = async (file: File) => {
    setBusy(true);
    try {
      const blob = await shrinkImage(file);
      setPreview(URL.createObjectURL(blob));
      onPick(blob);
    } catch (err) {
      toast.error(errorMessage(err, "Couldn't use that picture"));
    } finally {
      setBusy(false);
      if (input.current) input.current.value = "";
    }
  };

  const clear = async () => {
    if (preview) { setPreview(null); onPick(null); return; }
    if (onRemove) { setBusy(true); try { await onRemove(); } finally { setBusy(false); } }
  };

  const hasPicture = !!preview || !!image;
  return (
    <div className="flex items-center gap-4">
      {preview ? (
        // eslint-disable-next-line @next/next/no-img-element -- local preview
        <img src={preview} alt="" className="h-24 w-24 rounded-2xl border border-line object-cover" />
      ) : (
        <ProductThumb name={name || "?"} image={image} className="h-24 w-24 rounded-2xl border border-line text-lg" />
      )}
      <div className="space-y-2">
        <input ref={input} type="file" accept="image/*" className="sr-only"
          onChange={(e) => { const f = e.target.files?.[0]; if (f) void choose(f); }} />
        <div className="flex flex-wrap gap-2">
          <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => input.current?.click()}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ImagePlus className="h-4 w-4" />}
            {hasPicture ? "Change picture" : "Add picture"}
          </Button>
          {hasPicture && (preview || onRemove) && (
            <Button type="button" size="sm" variant="ghost" disabled={busy} onClick={clear}>
              <Trash2 className="h-4 w-4" /> Remove
            </Button>
          )}
        </div>
        <p className="text-xs text-ink-3">Shown on the POS and product grid. Any photo works: it&apos;s resized for you.</p>
      </div>
    </div>
  );
}
