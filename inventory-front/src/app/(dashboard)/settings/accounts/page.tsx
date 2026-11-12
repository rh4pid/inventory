"use client";
import { errorMessage } from "@/lib/utils";
import { useEffect, useState, useCallback } from "react";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { accounts as accountsApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Account } from "@/Types";

const TYPE_COLOR: Record<string, "default" | "success" | "destructive" | "warning" | "secondary"> = {
  ASSET:     "success",
  LIABILITY: "destructive",
  EQUITY:    "default",
  REVENUE:   "success",
  EXPENSE:   "warning",
};

export default function AccountsPage() {
  const { activeBusiness } = useBusiness();
  const [items, setItems] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [setting, setSetting] = useState(false);

  const load = useCallback(async () => {
    if (!activeBusiness) return;
    setLoading(true);
    try {
      const list = await accountsApi.list(activeBusiness.businessId);
      setItems(list);
    } catch (err) { toast.error(errorMessage(err)); }
    setLoading(false);
  }, [activeBusiness]);

  useEffect(() => { load(); }, [load]);

  const handleSetupDefaults = async () => {
    if (!activeBusiness) return;
    setSetting(true);
    try {
      await accountsApi.setupDefaults(activeBusiness.businessId);
      toast.success("Default accounts created");
      load();
    } catch (err) {
      toast.error(errorMessage(err, "Setup failed"));
    } finally {
      setSetting(false);
    }
  };

  if (loading && items.length === 0) return <PageLoader />;

  return (
    <div>
      <PageHeader
        title="Chart of Accounts"
        description="Manage your double-entry accounting accounts"
        action={
          <Button variant="outline" onClick={handleSetupDefaults} disabled={setting}>
            <RefreshCw className={`h-4 w-4 ${setting ? "animate-spin" : ""}`} />
            {setting ? "Setting up…" : "Setup Defaults"}
          </Button>
        }
      />

      {items.length === 0 && !loading ? (
        <EmptyState
          title="No accounts configured"
          description="Click 'Setup Defaults' to create the standard chart of accounts for sales, purchases, inventory, and more."
          action={
            <Button onClick={handleSetupDefaults} disabled={setting}>
              Setup Defaults
            </Button>
          }
        />
      ) : (
        <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Code</TableHead>
                <TableHead>Account Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Purpose</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((a) => (
                <TableRow key={a.id}>
                  <TableCell className="font-mono text-sm text-gray-600">{a.accountCode}</TableCell>
                  <TableCell className="font-medium text-sm">{a.accountName}</TableCell>
                  <TableCell>
                    <Badge variant={TYPE_COLOR[a.accountType] ?? "secondary"}>
                      {a.accountType}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-xs text-gray-500">
                    {a.accountPurpose?.replace(/_/g, " ") ?? "—"}
                  </TableCell>
                  <TableCell>
                    <Badge variant={a.active ? "success" : "secondary"}>
                      {a.active ? "Active" : "Inactive"}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
