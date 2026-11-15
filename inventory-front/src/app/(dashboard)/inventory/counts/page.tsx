"use client";
import { DataView, ViewToggle, useViewMode } from "@/components/shared/DataView";
import { errorMessage } from "@/lib/utils";
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, ClipboardList } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { shops as shopsApi, stockCounts as countsApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { COUNT_STATUS } from "@/constants/Inventory";
import type { Shop, StockCount } from "@/Types";


export default function StockCountsPage() {
  const [view, setView] = useViewMode("stock-counts");
  const router = useRouter();
  const { activeBusiness } = useBusiness();
  const [list, setList] = useState<StockCount[] | null>(null);
  const [shops, setShops] = useState<Shop[]>([]);
  const [starting, setStarting] = useState(false);
  const [shopId, setShopId] = useState("");
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!activeBusiness) return;
    try {
      const [counts, shopRes] = await Promise.all([
        countsApi.list(activeBusiness.businessId),
        shopsApi.list(activeBusiness.businessId, { mine: true }),
      ]);
      setList(counts);
      setShops(shopRes.content);
    } catch (err) { toast.error(errorMessage(err)); setList([]); }
  }, [activeBusiness]);

  useEffect(() => { load(); }, [load]);

  const start = async () => {
    if (!activeBusiness || !shopId) return;
    setBusy(true);
    try {
      const v = await countsApi.start(activeBusiness.businessId, Number(shopId), notes.trim() || undefined);
      router.push(`/inventory/counts/${v.count.id}`);
    } catch (err) {
      toast.error(errorMessage(err));
      setBusy(false);
    }
  };

  if (!list) return <PageLoader />;

  return (
    <div>
      <Link href="/inventory" className="mb-3 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-800">
        <ArrowLeft className="h-4 w-4" /> Inventory
      </Link>
      <PageHeader view={<ViewToggle mode={view} onChange={setView} />}
        title="Stock counts"
        description="Count what is physically on the shelves and correct the system in one go."
        action={<Button onClick={() => setStarting(true)} disabled={shops.length === 0}>
          <ClipboardList className="h-4 w-4" /> Start a count
        </Button>}
      />

      {list.length === 0 ? (
        <EmptyState title="No counts yet" description="Start a count for a location to check stock against the system." />
      ) : (
        <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-x-auto">
          <DataView mode={view}><Table>
            <TableHeader>
              <TableRow>
                <TableHead>Reference</TableHead>
                <TableHead>Location</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Started</TableHead>
                <TableHead>Posted</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.map((c) => (
                <TableRow key={c.id}>
                  <TableCell className="font-mono text-xs">
                    <Link href={`/inventory/counts/${c.id}`} className="text-blue-600 hover:underline">{c.reference}</Link>
                  </TableCell>
                  <TableCell className="text-sm">{c.shopName}</TableCell>
                  <TableCell><Badge variant={COUNT_STATUS[c.status].variant}>{COUNT_STATUS[c.status].label}</Badge></TableCell>
                  <TableCell className="text-xs text-gray-500">
                    {new Date(c.createdAt).toLocaleString()}<span className="block">{c.createdBy}</span>
                  </TableCell>
                  <TableCell className="text-xs text-gray-500">
                    {c.postedAt ? <>{new Date(c.postedAt).toLocaleString()}<span className="block">{c.postedBy}</span></> : "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table></DataView>
        </div>
      )}

      <Dialog open={starting} onOpenChange={(o) => !o && setStarting(false)}>
        <DialogContent>
          <DialogHeader><DialogTitle>Start a stock count</DialogTitle></DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label>Location *</Label>
              <Select value={shopId} onValueChange={setShopId}>
                <SelectTrigger><SelectValue placeholder="Select location" /></SelectTrigger>
                <SelectContent>
                  {shops.map((s) => <SelectItem key={s.id} value={String(s.id)}>{s.shopName}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>Notes</Label>
              <Input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="e.g. Month-end count" />
            </div>
            <p className="text-xs text-gray-500">
              The system quantity of every product here is recorded now. Sales can continue while you count:
              only the difference you find is applied when the count is posted.
            </p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setStarting(false)}>Cancel</Button>
            <Button onClick={start} disabled={!shopId || busy}>{busy ? "Starting…" : "Start count"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
