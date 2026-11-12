"use client";
import { useEffect, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { useForm, type Resolver } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { products as productsApi, categories as categoriesApi, units as unitsApi } from "@/lib/api";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { PackSizesEditor, toPackPayload, toPackRows, type PackRow } from "@/components/products/PackSizesEditor";
import { PageHeader } from "@/components/shared/PageHeader";
import { PageLoader } from "@/components/shared/LoadingSpinner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { Category, Product, ProductUnit, Unit } from "@/Types";

const schema = z.object({
  productName:   z.string().min(1, "Required"),
  description:   z.string().optional(),
  sku:           z.string().optional(),
  barcode:       z.string().optional(),
  costPrice:     z.coerce.number().positive("Must be positive"),
  sellingPrice:  z.coerce.number().positive("Must be positive"),
  reorderLevel:  z.coerce.number().min(0).optional(),
  categoryId:    z.coerce.number().optional(),
  unitId:        z.coerce.number().optional(),
  discountType:  z.string().optional(),
  discountValue: z.coerce.number().min(0).optional(),
});

type FormData = z.infer<typeof schema>;

export default function EditProductPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const { activeBusiness } = useBusiness();
  const [cats, setCats] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [unitList, setUnitList] = useState<Unit[]>([]);
  const [packRows, setPackRows] = useState<PackRow[]>([]);
  const { settings } = useBusinessSettings();

  const { register, handleSubmit, setValue, reset, watch, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema) as Resolver<FormData>,
  });
  const baseUnitId = watch("unitId");
  const categoryId = watch("categoryId");

  useEffect(() => {
    if (!activeBusiness || !params.id) return;
    const id = activeBusiness.businessId;
    Promise.all([
      productsApi.get(id, Number(params.id)),
      categoriesApi.list(id),
      unitsApi.list(id),
      unitsApi.forProduct(id, Number(params.id)),
    ]).then(([product, catList, unitRes, packs]: [Product, Category[], Unit[], ProductUnit[]]) => {
      setUnitList(unitRes);
      setPackRows(toPackRows(packs));
      reset({
        productName:   product.productName,
        description:   product.description,
        sku:           product.sku,
        barcode:       product.barcode,
        costPrice:     product.costPrice,
        sellingPrice:  product.sellingPrice,
        reorderLevel:  product.reorderLevel,
        categoryId:    product.category?.id,
        unitId:        product.unit?.id,
        discountType:  product.discountType,
        discountValue: product.discountValue,
      });
      setCats(catList);
    }).catch((err: Error) => toast.error(err.message ?? "Load failed"))
      .finally(() => setLoading(false));
  }, [activeBusiness, params.id, reset]);

  const onSubmit = async (data: FormData) => {
    if (!activeBusiness) return;
    const canEditPacks = ["OWNER", "ADMIN", "MANAGER"].includes(activeBusiness.role);
    const packs = settings?.multipleUnits && canEditPacks ? toPackPayload(packRows) : null;
    if (typeof packs === "string") { toast.error(packs); return; }
    try {
      await productsApi.update(activeBusiness.businessId, Number(params.id), {
        ...data,
        categoryId: data.categoryId || undefined,
        unitId: data.unitId || undefined,
      });
      // Only touch pack sizes when the feature is on, so turning it off never deletes them.
      if (packs) {
        await unitsApi.setForProduct(activeBusiness.businessId, Number(params.id), packs);
      }
      toast.success("Product updated");
      router.push("/products");
    } catch (err: any) {
      toast.error(err.message ?? "Update failed");
    }
  };

  if (loading) return <PageLoader />;

  return (
    <div className="max-w-2xl">
      <PageHeader title="Edit Product" />

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        <Card>
          <CardHeader><CardTitle className="text-base">Basic Info</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1.5">
              <Label>Product name *</Label>
              <Input {...register("productName")} />
              {errors.productName && <p className="text-xs text-red-600">{errors.productName.message}</p>}
            </div>
            <div className="space-y-1.5">
              <Label>Description</Label>
              <Textarea {...register("description")} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>SKU</Label>
                <Input {...register("sku")} />
              </div>
              <div className="space-y-1.5">
                <Label>Barcode</Label>
                <Input {...register("barcode")} />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>Category</Label>
              <Select value={categoryId ? String(categoryId) : ""} onValueChange={(v) => setValue("categoryId", Number(v))}>
                <SelectTrigger><SelectValue placeholder="Select category" /></SelectTrigger>
                <SelectContent>
                  {cats.map((c) => (
                    <SelectItem key={c.id} value={String(c.id)}>{c.categoryName}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="text-base">Pricing & Stock</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Cost price *</Label>
                <Input type="number" step="0.01" {...register("costPrice")} />
                {errors.costPrice && <p className="text-xs text-red-600">{errors.costPrice.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>Selling price *</Label>
                <Input type="number" step="0.01" {...register("sellingPrice")} />
                {errors.sellingPrice && <p className="text-xs text-red-600">{errors.sellingPrice.message}</p>}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Reorder level</Label>
                <Input type="number" min="0" {...register("reorderLevel")} />
              </div>
              <div className="space-y-1.5">
                <Label>Discount value</Label>
                <Input type="number" step="0.01" min="0" {...register("discountValue")} />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="text-base">Units</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1.5">
              <Label>Base unit (how stock is counted)</Label>
              <Select value={baseUnitId ? String(baseUnitId) : ""} onValueChange={(v) => setValue("unitId", Number(v))}>
                <SelectTrigger><SelectValue placeholder="Select unit" /></SelectTrigger>
                <SelectContent>
                  {unitList.map((u) => <SelectItem key={u.id} value={String(u.id)}>{u.name}</SelectItem>)}
                </SelectContent>
              </Select>
              <p className="text-xs text-gray-500">
                Cost and selling price above are per base unit. Changing it does not convert existing stock.
              </p>
            </div>
            {settings?.multipleUnits && (
              <div className="space-y-1.5">
                <Label>Pack sizes</Label>
                <PackSizesEditor units={unitList} baseUnitId={baseUnitId} rows={packRows} onChange={setPackRows} />
              </div>
            )}
          </CardContent>
        </Card>

        <div className="flex gap-3">
          <Button type="button" variant="outline" onClick={() => router.back()}>Cancel</Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Saving…" : "Save Changes"}
          </Button>
        </div>
      </form>
    </div>
  );
}
