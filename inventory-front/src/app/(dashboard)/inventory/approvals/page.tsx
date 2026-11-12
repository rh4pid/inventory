"use client";
import { errorMessage } from "@/lib/utils";
import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Check, X } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { useAuth } from "@/context/AuthProvider";
import { stockRequests as requestsApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { StockRequest, StockRequestStatus } from "@/Types";
import { useCan } from "@/lib/access";

const STATUS_VARIANT: Record<StockRequestStatus, "warning" | "success" | "destructive" | "secondary"> = {
  PENDING: "warning", APPROVED: "success", REJECTED: "destructive", CANCELLED: "secondary",
};

export default function ApprovalsPage() {
  const { activeBusiness } = useBusiness();
  const { user } = useAuth();
  const [list, setList] = useState<StockRequest[] | null>(null);
  const [filter, setFilter] = useState<StockRequestStatus | "ALL">("PENDING");
  const [busyId, setBusyId] = useState<number | null>(null);
  const isManager = useCan()("APPROVE");

  const load = useCallback(async () => {
    if (!activeBusiness) return;
    try {
      setList(await requestsApi.list(activeBusiness.businessId, filter === "ALL" ? undefined : filter));
    } catch (err) { toast.error(errorMessage(err)); setList([]); }
  }, [activeBusiness, filter]);

  useEffect(() => { load(); }, [load]);

  const act = async (r: StockRequest, kind: "approve" | "reject" | "cancel") => {
    if (!activeBusiness) return;
    let note: string | undefined;
    if (kind === "reject") {
      const answer = window.prompt("Reason for rejecting (optional):");
      if (answer === null) return;
      note = answer;
    }
    setBusyId(r.id);
    try {
      const id = activeBusiness.businessId;
      if (kind === "approve") await requestsApi.approve(id, r.id, note);
      else if (kind === "reject") await requestsApi.reject(id, r.id, note);
      else await requestsApi.cancel(id, r.id);
      toast.success(kind === "approve" ? "Approved: stock updated" : kind === "reject" ? "Rejected" : "Request cancelled");
      load();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusyId(null);
    }
  };

  if (!list) return <PageLoader />;

  return (
    <div>
      <Link href="/inventory" className="mb-3 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-800">
        <ArrowLeft className="h-4 w-4" /> Inventory
      </Link>
      <PageHeader
        title="Stock approvals"
        description={isManager
          ? "Write-offs and adjustments by cashiers and staff wait here until you approve them."
          : "Your write-offs and adjustments wait here until a manager approves them."}
      />

      <div className="mb-3 flex flex-wrap gap-1 text-sm" role="tablist" aria-label="Filter by status">
        {(["PENDING", "APPROVED", "REJECTED", "CANCELLED", "ALL"] as const).map((s) => (
          <button key={s} role="tab" aria-selected={filter === s} onClick={() => { setList(null); setFilter(s); }}
            className={`rounded-md px-3 py-1.5 font-medium ${filter === s ? "bg-gray-900 text-white" : "text-gray-600 hover:bg-gray-100"}`}>
            {s === "ALL" ? "All" : s.charAt(0) + s.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {list.length === 0 ? (
        <EmptyState title={filter === "PENDING" ? "Nothing waiting" : "No requests"}
          description={filter === "PENDING" ? "All stock changes have been reviewed." : "No requests with this status."} />
      ) : (
        <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Requested</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Product / location</TableHead>
                <TableHead>Change</TableHead>
                <TableHead>Reason</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-48" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {list.map((r) => {
                const mine = user?.email?.toLowerCase() === r.requestedBy.toLowerCase();
                return (
                  <TableRow key={r.id}>
                    <TableCell className="text-xs text-gray-500 whitespace-nowrap">
                      {new Date(r.createdAt).toLocaleString()}
                      <span className="block">{r.requestedBy}</span>
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">{r.requestType === "STOCK_OUT" ? "Write-off / out" : "Adjustment"}</Badge>
                    </TableCell>
                    <TableCell className="text-sm">
                      <span className="font-medium">{r.productName}</span>
                      <span className="block text-xs text-gray-500">{r.shopName}</span>
                    </TableCell>
                    <TableCell className="text-sm">{r.summary}</TableCell>
                    <TableCell className="text-xs text-gray-600 max-w-xs">
                      {[r.reason, r.notes].filter(Boolean).join(" | ") || "—"}
                    </TableCell>
                    <TableCell>
                      <Badge variant={STATUS_VARIANT[r.status]}>{r.status.toLowerCase()}</Badge>
                      {r.decidedBy && (
                        <span className="block text-[11px] text-gray-400 mt-0.5">
                          by {r.decidedBy}{r.decisionNote ? `: ${r.decisionNote}` : ""}
                        </span>
                      )}
                    </TableCell>
                    <TableCell>
                      {r.status === "PENDING" && (
                        <div className="flex justify-end gap-1">
                          {isManager && !mine && (
                            <>
                              <Button size="sm" onClick={() => act(r, "approve")} disabled={busyId === r.id}>
                                <Check className="h-4 w-4" /> Approve
                              </Button>
                              <Button size="sm" variant="outline" onClick={() => act(r, "reject")} disabled={busyId === r.id}>
                                <X className="h-4 w-4" /> Reject
                              </Button>
                            </>
                          )}
                          {mine && (
                            <Button size="sm" variant="ghost" onClick={() => act(r, "cancel")} disabled={busyId === r.id}>
                              Cancel
                            </Button>
                          )}
                        </div>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
