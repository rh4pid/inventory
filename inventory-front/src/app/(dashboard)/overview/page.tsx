"use client";
import { useCallback, useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { useBusiness } from "@/context/BusinessContext";
import { useWorkspace, PERIODS } from "@/context/WorkspaceContext";
import { dashboard as dashboardApi } from "@/lib/api";
import { EmptyState } from "@/components/shared/EmptyState";
import { InvoiceDialog } from "@/components/shared/InvoiceDialog";
import { Button } from "@/components/ui/button";
import { OrdersCard } from "@/components/dashboard/OrdersCard";
import { StockCard } from "@/components/dashboard/StockCard";
import { OrderHistoryCard } from "@/components/dashboard/OrderHistoryCard";
import { StockFlowCard } from "@/components/dashboard/StockFlowCard";
import { ProductListCard } from "@/components/dashboard/ProductListCard";
import { SetupChecklist } from "@/components/dashboard/SetupChecklist";
import type { DashboardData, Invoice } from "@/Types";

const PERIOD_PHRASE: Record<string, string> = {
  week: "in the last 7 days", month: "in the last 30 days", quarter: "in the last 90 days", year: "in the last year",
};

function Skeleton({ className }: { className: string }) {
  return <div className={`animate-pulse rounded-4xl bg-surface-2 ${className}`} />;
}

export default function OverviewPage() {
  const { activeBusiness } = useBusiness();
  const { locationId, location, period } = useWorkspace();
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [opened, setOpened] = useState<Invoice | null>(null);
  const bizId = activeBusiness?.businessId;

  const load = useCallback(async () => {
    if (!bizId) return;
    setError(null);
    try {
      setData(await dashboardApi.get(bizId, { shopId: locationId ?? undefined, period }));
    } catch (err: any) {
      setError(err.message ?? "Could not load the dashboard");
    }
  }, [bizId, locationId, period]);

  useEffect(() => { setData(null); load(); }, [load]);

  if (!activeBusiness) {
    return <EmptyState title="No business selected" description="You are not a member of any business yet." />;
  }

  if (error) {
    return (
      <EmptyState
        title="Couldn't load the dashboard"
        description={error}
        action={<Button variant="outline" onClick={load}><RefreshCw className="h-4 w-4" /> Try again</Button>}
      />
    );
  }

  if (!data) {
    return (
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12 xl:gap-5" aria-busy="true" aria-label="Loading dashboard">
        <Skeleton className="h-[19rem] xl:col-span-4" />
        <Skeleton className="h-[19rem] xl:col-span-4" />
        <Skeleton className="h-[19rem] md:col-span-2 xl:col-span-4 xl:row-span-2 xl:h-auto" />
        <Skeleton className="h-[19rem] md:col-span-2 xl:col-span-8" />
        <Skeleton className="h-96 md:col-span-2 xl:col-span-12" />
      </div>
    );
  }

  const periodLabel = PERIOD_PHRASE[data.period] ?? `in the last ${data.days} days`;
  const locationName = location?.shopName ?? "All locations";

  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12 xl:gap-5">
      <h1 className="sr-only">Overview · {locationName} · {PERIODS.find((p) => p.value === period)?.label}</h1>
      <div className="md:col-span-2 xl:col-span-12 empty:hidden"><SetupChecklist hasStock={data.stock.products > 0} /></div>
      <div className="xl:col-span-4"><OrdersCard orders={data.orders} periodLabel={periodLabel} /></div>
      <div className="xl:col-span-4"><StockCard stock={data.stock} /></div>
      <div className="md:col-span-2 xl:col-span-4 xl:row-span-2">
        <OrderHistoryCard
          orders={data.recentOrders}
          onOpen={(o) => setOpened({ id: o.id, invoiceNumber: o.invoiceNumber, type: o.type } as Invoice)}
        />
      </div>
      <div className="md:col-span-2 xl:col-span-8">
        <StockFlowCard flow={data.flow} attention={data.attention} attentionTotal={data.attentionTotal} />
      </div>
      <div className="md:col-span-2 xl:col-span-12">
        <ProductListCard rows={data.products} locationName={locationName} />
      </div>

      {opened && <InvoiceDialog invoice={opened} onClose={() => setOpened(null)} onChanged={load} />}
    </div>
  );
}
