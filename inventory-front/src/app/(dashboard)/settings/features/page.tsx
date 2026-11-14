"use client";
import { errorMessage } from "@/lib/utils";
import { confirmAction } from "@/context/ConfirmProvider";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { settings as settingsApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { BusinessSettings, BusinessSettingsPayload, BusinessType, BusinessTypeOption } from "@/Types";
import { useCan } from "@/lib/access";

type FlagKey = "multipleUnits" | "trackBatches" | "trackExpiry" | "allowNegativeStock"
  | "requireAdjustmentApproval" | "lowStockAlerts";

const FLAGS: { key: FlagKey; title: string; description: string; comingSoon?: boolean }[] = [
  {
    key: "multipleUnits",
    title: "Pack sizes",
    description: "Buy and sell products in cartons, boxes, dozens… Stock is always counted in the base unit.",
  },
  {
    key: "trackBatches",
    title: "Batches / lots",
    description: "Keep stock in batches with lot number, cost and receive date. Sales take the oldest stock first and record which lots they came from.",
  },
  {
    key: "trackExpiry",
    title: "Expiry dates",
    description: "Record expiry dates on batches, sell the earliest-expiring stock first and block selling expired stock. Turns on batches.",
  },
  {
    key: "requireAdjustmentApproval",
    title: "Approve adjustments",
    description: "Write-offs and adjustments by cashiers and staff wait for a manager's approval, and their stock counts must be posted by a manager.",
  },
  {
    key: "allowNegativeStock",
    title: "Allow negative stock",
    description: "Let sales go through even when the system shows no stock (e.g. delivery not yet recorded).",
  },
  {
    key: "lowStockAlerts",
    title: "Low-stock alerts",
    description: "Highlight products at or below their reorder level.",
  },
];

export default function FeaturesSettingsPage() {
  const { activeBusiness } = useBusiness();
  const { settings, setSettings } = useBusinessSettings();
  const [types, setTypes] = useState<BusinessTypeOption[]>([]);
  const [saving, setSaving] = useState(false);
  const canEdit = useCan()("MANAGE_SETTINGS");

  useEffect(() => {
    settingsApi.businessTypes().then(setTypes).catch(() => {});
  }, []);

  if (!settings) return <PageLoader />;

  const save = async (patch: BusinessSettingsPayload) => {
    if (!activeBusiness) return;
    setSaving(true);
    try {
      const updated = await settingsApi.update(activeBusiness.businessId, patch);
      setSettings(updated);
      toast.success("Settings saved");
    } catch (err) {
      toast.error(errorMessage(err, "Could not save settings"));
    } finally {
      setSaving(false);
    }
  };

  const changeType = async (value: string) => {
    const type = value as BusinessType;
    const preset = types.find((t) => t.value === type);
    const summary = preset
      ? [preset.multipleUnits && "pack sizes", preset.trackBatches && "batches",
         preset.trackExpiry && "expiry dates", preset.requireAdjustmentApproval && "approvals"]
          .filter(Boolean).join(", ") || "no extra features"
      : "";
    const apply = await confirmAction({
      title: `Switch on the recommended features for ${preset?.label ?? type}?`,
      description: `Recommended: ${summary}. Either way the business type changes.`,
      confirmLabel: "Apply recommended features",
      cancelLabel: "Only change the type",
    });
    save({ businessType: type, applyPreset: apply });
  };

  return (
    <div className="max-w-2xl">
      <PageHeader
        title="Business type & features"
        description="Turn on only what your business needs. A provision shop, a pharmacy and a warehouse can all use the same system."
      />

      {!canEdit && (
        <p className="mb-4 rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-600">
          Only the owner or an admin can change these settings.
        </p>
      )}

      <div className="space-y-6">
        <Card>
          <CardHeader><CardTitle className="text-base">Business type</CardTitle></CardHeader>
          <CardContent className="space-y-1.5">
            <Label>What kind of business is this?</Label>
            <Select value={settings.businessType} onValueChange={changeType} disabled={!canEdit || saving}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {types.map((t) => <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>)}
              </SelectContent>
            </Select>
            <p className="text-xs text-gray-500">
              Choosing a type can switch on the features that type usually needs. You can still change each one below.
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="text-base">Stock features</CardTitle></CardHeader>
          <CardContent className="divide-y divide-gray-100">
            {FLAGS.map((f) => (
              <div key={f.key} className="flex items-start justify-between gap-4 py-3 first:pt-0 last:pb-0">
                <div>
                  <p className="text-sm font-medium text-gray-900">
                    {f.title}
                    {f.comingSoon && (
                      <span className="ml-2 rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-medium uppercase text-gray-500">
                        Coming next
                      </span>
                    )}
                  </p>
                  <p className="text-xs text-gray-500 mt-0.5">{f.description}</p>
                </div>
                <Switch
                  checked={Boolean(settings[f.key as keyof BusinessSettings])}
                  disabled={!canEdit || saving}
                  onCheckedChange={(v) => save({ [f.key]: v })}
                />
              </div>
            ))}
            {settings.trackExpiry && (
              <ExpiryDays
                value={settings.expiryAlertDays}
                disabled={!canEdit || saving}
                onSave={(d) => save({ expiryAlertDays: d })}
              />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function ExpiryDays({ value, disabled, onSave }: { value: number; disabled: boolean; onSave: (d: number) => void }) {
  const [days, setDays] = useState(String(value));
  return (
    <div className="flex items-end gap-2 py-3">
      <div className="space-y-1.5">
        <Label>Warn this many days before expiry</Label>
        <Input type="number" min={1} max={365} value={days} onChange={(e) => setDays(e.target.value)} className="w-32" />
      </div>
      <Button
        variant="outline" size="sm" disabled={disabled || Number(days) < 1}
        onClick={() => onSave(Number(days))}
      >
        Save
      </Button>
    </div>
  );
}
