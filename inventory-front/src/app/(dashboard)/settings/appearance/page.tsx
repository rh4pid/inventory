"use client";
import { useState } from "react";
import { Check, Sparkles } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { settings as settingsApi } from "@/lib/api";
import { THEMES, defaultThemeFor, resolveTheme, type Theme } from "@/lib/themes";
import { cn, errorMessage } from "@/lib/utils";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";

const TYPE_LABEL: Record<string, string> = {
  GENERAL_RETAIL: "general retail", PROVISION_SHOP: "provision shops", SUPERMARKET: "supermarkets",
  PHARMACY: "pharmacies", WAREHOUSE: "warehouses", HARDWARE: "hardware stores", RESTAURANT: "restaurants", OTHER: "other businesses",
};

/** A small picture of the dashboard in a theme's colours. */
function Preview({ theme }: { theme: Theme }) {
  const v = theme.vars;
  return (
    <div className="rounded-2xl p-2" style={{ background: v.canvas }} aria-hidden="true">
      <div className="flex gap-1.5 rounded-xl p-2" style={{ background: v.frame }}>
        <div className="flex w-4 flex-col gap-1">
          <span className="h-4 rounded-md" style={{ background: v.accent }} />
          {[0, 1, 2].map((i) => <span key={i} className="h-4 rounded-md" style={{ background: v.surface2 }} />)}
        </div>
        <div className="grid flex-1 grid-cols-3 gap-1.5">
          <div className="flex h-16 items-end gap-1 rounded-lg p-1.5" style={{ background: v.brandStrong }}>
            {[30, 50, 70, 95].map((h) => <span key={h} className="flex-1 rounded-t-sm bg-white/80" style={{ height: `${h}%` }} />)}
          </div>
          <div className="relative h-16 overflow-hidden rounded-lg" style={{ background: v.accent }}>
            <span className="absolute -bottom-5 -right-3 h-14 w-14 rounded-full border-[7px]" style={{ borderColor: "#1c2541" }} />
          </div>
          <div className="h-16 rounded-lg border bg-white p-1.5" style={{ borderColor: v.line }}>
            <span className="block h-1.5 w-2/3 rounded" style={{ background: v.surface2 }} />
            <span className="mt-1 block h-1.5 w-1/2 rounded" style={{ background: v.surface2 }} />
            <span className="mt-2 block h-4 rounded" style={{ background: v.brandSoft }} />
          </div>
          <div className="col-span-3 flex h-9 items-end gap-0.5 rounded-lg bg-white px-1.5 pb-1">
            {[40, 70, 30, 90, 55, 75, 45, 85, 60, 35].map((h, i) => (
              <span key={i} className="flex-1 rounded-t-sm" style={{ height: `${h}%`, background: i % 2 ? v.accent : v.brand }} />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function AppearancePage() {
  const { activeBusiness } = useBusiness();
  const { settings, setSettings } = useBusinessSettings();
  const [saving, setSaving] = useState<string | null>(null);

  if (!settings || !activeBusiness) return <PageLoader />;

  const auto = defaultThemeFor(settings.businessType);
  const current = resolveTheme(settings.theme, settings.businessType);
  const isAuto = !settings.theme;
  const typeLabel = TYPE_LABEL[settings.businessType] ?? "your business type";

  const choose = async (themeId: string) => {
    setSaving(themeId);
    try {
      const saved = await settingsApi.update(activeBusiness.businessId, { theme: themeId });
      setSettings(saved);
      const t = resolveTheme(saved.theme, saved.businessType);
      toast.success(themeId === "auto" ? `Automatic: ${t.name}` : `${t.name} theme applied`);
    } catch (err) {
      toast.error(errorMessage(err, "Could not change the theme"));
    } finally {
      setSaving(null);
    }
  };

  return (
    <div className="max-w-5xl">
      <PageHeader
        title="Appearance"
        description={`How ${activeBusiness.businessName} looks for everyone in the company. Layout stays the same; colours change.`}
      />

      <button
        onClick={() => choose("auto")}
        disabled={saving !== null}
        aria-pressed={isAuto}
        className={cn(
          "mb-5 flex w-full items-center gap-4 rounded-3xl border bg-surface p-4 text-left shadow-card transition-colors",
          isAuto ? "border-brand ring-2 ring-brand/25" : "border-line/80 hover:border-brand/40"
        )}
      >
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-brand-soft text-brand-deep">
          <Sparkles className="h-5 w-5" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block font-semibold text-ink">Automatic</span>
          <span className="block text-sm text-ink-3">Use the theme made for {typeLabel}: <b className="font-medium text-ink-2">{auto.name}</b>. Changes if you change the business type.</span>
        </span>
        {isAuto && <Check className="h-5 w-5 shrink-0 text-brand" />}
      </button>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {THEMES.map((t) => {
          const selected = !isAuto && current.id === t.id;
          const recommended = t.suits.includes(settings.businessType);
          return (
            <button
              key={t.id}
              onClick={() => choose(t.id)}
              disabled={saving !== null}
              aria-pressed={selected}
              aria-label={`${t.name} theme${recommended ? " (recommended)" : ""}`}
              className={cn(
                "group rounded-3xl border bg-surface p-3 text-left shadow-card transition-all hover:shadow-float disabled:cursor-wait",
                selected ? "border-brand ring-2 ring-brand/25" : "border-line/80"
              )}
            >
              <Preview theme={t} />
              <div className="mt-3 flex items-start justify-between gap-2 px-1 pb-1">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-ink">{t.name}</span>
                    {recommended && (
                      <span className="rounded-md bg-accent-soft px-1.5 py-0.5 text-[11px] font-semibold text-accent-deep">Recommended</span>
                    )}
                  </div>
                  <p className="mt-0.5 text-xs text-ink-3">{t.tagline}</p>
                </div>
                <span className="flex shrink-0 gap-1 pt-0.5" aria-hidden="true">
                  <span className="h-4 w-4 rounded-full" style={{ background: t.vars.brand }} />
                  <span className="h-4 w-4 rounded-full" style={{ background: t.vars.accent }} />
                </span>
              </div>
              {saving === t.id && <p className="px-1 text-xs text-ink-3">Applying…</p>}
              {selected && <p className="flex items-center gap-1 px-1 text-xs font-medium text-brand-deep"><Check className="h-3.5 w-3.5" /> In use</p>}
            </button>
          );
        })}
      </div>
    </div>
  );
}
