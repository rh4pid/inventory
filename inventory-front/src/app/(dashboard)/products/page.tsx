"use client";
import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { Plus, Search, Pencil, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { products as productsApi } from "@/lib/api";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { formatCurrency } from "@/lib/utils";
import type { Product } from "@/Types";

export default function ProductsPage() {
  const { activeBusiness } = useBusiness();
  const [items, setItems] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  // Deep link from the dashboard / search: /products?q=…
  useEffect(() => {
    const q = new URLSearchParams(window.location.search).get("q");
    if (q) setSearch(q);
  }, []);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [deleting, setDeleting] = useState(false);

  const SIZE = 20;

  const load = useCallback(async () => {
    if (!activeBusiness) return;
    setLoading(true);
    try {
      const res = await productsApi.list(activeBusiness.businessId, {
        page, size: SIZE, search: search || undefined,
      });
      setItems(res.content);
      setTotal(res.totalElements);
    } catch (err: any) {
      toast.error(err.message ?? "Failed to load products");
    } finally {
      setLoading(false);
    }
  }, [activeBusiness, page, search]);

  useEffect(() => { load(); }, [load]);

  const handleDelete = async () => {
    if (!activeBusiness || deleteId == null) return;
    setDeleting(true);
    try {
      await productsApi.delete(activeBusiness.businessId, deleteId);
      toast.success("Product deleted");
      setDeleteId(null);
      load();
    } catch (err: any) {
      toast.error(err.message ?? "Delete failed");
    } finally {
      setDeleting(false);
    }
  };

  if (loading && items.length === 0) return <PageLoader />;

  return (
    <div>
      <PageHeader
        title="Products"
        description={`${total} items in catalog`}
        action={
          <Link href="/products/new">
            <Button><Plus className="h-4 w-4" /> Add Product</Button>
          </Link>
        }
      />

      <div className="mb-4 flex gap-3">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
          <Input
            placeholder="Search products…"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            className="pl-9"
          />
        </div>
      </div>

      {items.length === 0 && !loading ? (
        <EmptyState
          title="No products yet"
          description="Add your first product to get started."
          action={<Link href="/products/new"><Button>Add Product</Button></Link>}
        />
      ) : (
        <div className="rounded-3xl border border-line/80 bg-surface shadow-card overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>SKU</TableHead>
                <TableHead>Category</TableHead>
                <TableHead>Cost</TableHead>
                <TableHead>Price</TableHead>
                <TableHead>Stock</TableHead>
                <TableHead className="w-20"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((p) => (
                <TableRow key={p.id}>
                  <TableCell>
                    <div>
                      <p className="font-medium text-sm">{p.productName}</p>
                      {p.description && (
                        <p className="text-xs text-gray-400 truncate max-w-xs">{p.description}</p>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="font-mono text-xs text-gray-500">{p.sku ?? "—"}</TableCell>
                  <TableCell>
                    {p.category ? (
                      <Badge variant="secondary">{p.category.categoryName}</Badge>
                    ) : "—"}
                  </TableCell>
                  <TableCell>{formatCurrency(p.costPrice)}</TableCell>
                  <TableCell className="font-medium">{formatCurrency(p.sellingPrice)}</TableCell>
                  <TableCell>
                    <span className={p.totalCount <= (p.reorderLevel ?? 0) && p.totalCount > 0
                      ? "text-yellow-600 font-semibold"
                      : p.totalCount === 0 ? "text-red-600 font-semibold" : ""}>
                      {p.totalCount}
                    </span>
                    {p.unit && <span className="text-xs text-gray-400 ml-1">{p.unit.symbol}</span>}
                  </TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      <Link href={`/products/${p.id}/edit`}>
                        <Button variant="ghost" size="icon" className="h-8 w-8">
                          <Pencil className="h-3.5 w-3.5" />
                        </Button>
                      </Link>
                      <Button
                        variant="ghost" size="icon" className="h-8 w-8 text-red-500 hover:text-red-600"
                        onClick={() => setDeleteId(p.id)}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {/* Pagination */}
          {total > SIZE && (
            <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
              <p className="text-xs text-gray-500">
                Showing {page * SIZE + 1}–{Math.min((page + 1) * SIZE, total)} of {total}
              </p>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                  Previous
                </Button>
                <Button variant="outline" size="sm" disabled={(page + 1) * SIZE >= total} onClick={() => setPage((p) => p + 1)}>
                  Next
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      <ConfirmDialog
        open={deleteId != null}
        onOpenChange={(o) => !o && setDeleteId(null)}
        title="Delete product?"
        description="This will soft-delete the product. Existing sales records will be unaffected."
        confirmLabel="Delete"
        onConfirm={handleDelete}
        loading={deleting}
      />
    </div>
  );
}
