"use client";
import { errorMessage } from "@/lib/utils";
import { useEffect, useState } from "react";
import { ProductImagePicker } from "@/components/products/ProductImage";
import { useRouter } from "next/navigation";
import { useForm, type Resolver } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { useBusiness } from "@/context/BusinessContext";
import { products as productsApi, categories as categoriesApi, units as unitsApi } from "@/lib/api";
import { useBusinessSettings } from "@/hooks/useBusinessSettings";
import { PackSizesEditor, toPackPayload, type PackRow } from "@/components/products/PackSizesEditor";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { Category, Unit } from "@/Types";

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

export default function NewProductPage() {
  const router = useRouter();
  const { activeBusiness } = useBusiness();
  const [cats, setCats] = useState<Category[]>([]);
  const [unitList, setUnitList] = useState<Unit[]>([]);
  const [packRows, setPackRows] = useState<PackRow[]>([]);
  const [picture, setPicture] = useState<Blob | null>(null);
  const { settings } = useBusinessSettings();

  const { register, handleSubmit, setValue, watch, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema) as Resolver<FormData>,
  });
  const baseUnitId = watch("unitId");
  const nameNow = watch("productName") ?? "";

  useEffect(() => {
    if (!activeBusiness) return;
    categoriesApi.list(activeBusiness.businessId)
      .then((list) => setCats(list))
      .catch(() => {});
    unitsApi.list(activeBusiness.businessId)
      .then((list) => {
        setUnitList(list);
        const piece = list.find((u) => u.name === "Piece");
        if (piece) setValue("unitId", piece.id);
      })
      .catch(() => {});
  }, [activeBusiness, setValue]);

  const onSubmit = async (data: FormData) => {
    if (!activeBusiness) return;
    const packs = settings?.multipleUnits ? toPackPayload(packRows) : [];
    if (typeof packs === "string") { toast.error(packs); return; }
    try {
      const created = await productsApi.create(activeBusiness.businessId, {
        ...data,
        categoryId: data.categoryId || undefined,
        unitId: data.unitId || undefined,
      });
      if (packs.length > 0) {
        await unitsApi.setForProduct(activeBusiness.businessId, created.id, packs);
      }
      if (picture) {
        try { await productsApi.uploadImage(activeBusiness.businessId, created.id, picture); }
        catch (err) { toast.error(errorMessage(err, "Product saved, but the picture couldn't be uploaded")); }
      }
      toast.success("Product created");
      router.push("/products");
    } catch (err) {
      toast.error(errorMessage(err, "Failed to create product"));
    }
  };

  return (
    <div className="max-w-2xl">
      <PageHeader title="Add Product" description="Create a new product in your catalog" />

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
        <Card>
          <CardHeader><CardTitle className="text-base">Basic Info</CardTitle></CardHeader>
          <CardContent className="space-y-4">
            <ProductImagePicker name={nameNow} onPick={setPicture} />
            <div className="space-y-1.5">
              <Label>Product name *</Label>
              <Input placeholder="e.g. Laptop Pro 15" {...register("productName")} />
              {errors.productName && <p className="text-xs text-red-600">{errors.productName.message}</p>}
            </div>
            <div className="space-y-1.5">
              <Label>Description</Label>
              <Textarea placeholder="Optional product description…" {...register("description")} />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>SKU</Label>
                <Input placeholder="SKU-001" {...register("sku")} />
              </div>
              <div className="space-y-1.5">
                <Label>Barcode</Label>
                <Input placeholder="123456789" {...register("barcode")} />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>Category</Label>
              <Select onValueChange={(v) => setValue("categoryId", Number(v))}>
                <SelectTrigger>
                  <SelectValue placeholder="Select category" />
                </SelectTrigger>
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
                <Input type="number" step="0.01" placeholder="0.00" {...register("costPrice")} />
                {errors.costPrice && <p className="text-xs text-red-600">{errors.costPrice.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>Selling price *</Label>
                <Input type="number" step="0.01" placeholder="0.00" {...register("sellingPrice")} />
                {errors.sellingPrice && <p className="text-xs text-red-600">{errors.sellingPrice.message}</p>}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Reorder level</Label>
                <Input type="number" min="0" placeholder="10" {...register("reorderLevel")} />
              </div>
              <div className="space-y-1.5">
                <Label>Discount value</Label>
                <Input type="number" step="0.01" min="0" placeholder="0" {...register("discountValue")} />
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
              <p className="text-xs text-gray-500">Cost and selling price above are per base unit.</p>
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
            {isSubmitting ? "Saving…" : "Create Product"}
          </Button>
        </div>
      </form>
    </div>
  );
}
