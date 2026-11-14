"use client";
import { errorMessage } from "@/lib/utils";
import { useState, useEffect } from "react";
import { useRouter, useParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { Resolver } from "react-hook-form";
import { toast } from "sonner";
import { employees as employeesApi } from "@/lib/api";
import { useBusiness } from "@/context/BusinessContext";
import type { SalaryType } from "@/Types";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";

const schema = z.object({
  position:   z.string().optional(),
  department: z.string().optional(),
  salary:     z.coerce.number().positive("Must be positive").optional(),
  salaryType: z.enum(["MONTHLY", "WEEKLY", "DAILY", "HOURLY"]).optional(),
  startDate:  z.string().optional(),
  endDate:    z.string().optional(),
});

type FormData = z.infer<typeof schema>;

const SALARY_TYPES: SalaryType[] = ["MONTHLY", "WEEKLY", "DAILY", "HOURLY"];

export default function EditEmployeePage() {
  const router = useRouter();
  const params = useParams();
  const id = Number(params.id);
  const { activeBusiness } = useBusiness();
  const bizId = activeBusiness?.businessId ?? 0;

  const [loading, setLoading] = useState(true);
  const [name, setName] = useState("");

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } =
    useForm<FormData>({ resolver: zodResolver(schema) as Resolver<FormData> });

  useEffect(() => {
    if (!bizId || !id) return;
    setLoading(true);
    employeesApi.get(bizId, id)
      .then((emp) => {
        setName(`${emp.businessUser.user.firstName} ${emp.businessUser.user.lastName}`);
        reset({
          position:   emp.position ?? "",
          department: emp.department ?? "",
          salary:     emp.salary,
          salaryType: emp.salaryType,
          startDate:  emp.startDate ? emp.startDate.split("T")[0] : "",
          endDate:    emp.endDate   ? emp.endDate.split("T")[0]   : "",
        });
      })
      .catch(() => toast.error("Employee not found"))
      .finally(() => setLoading(false));
  }, [bizId, id, reset]);

  const onSubmit = async (data: FormData) => {
    try {
      await employeesApi.update(bizId, id, {
        position:   data.position,
        department: data.department,
        salary:     data.salary,
        salaryType: data.salaryType,
        startDate:  data.startDate,
        endDate:    data.endDate,
      });
      toast.success("Employee updated");
      router.push("/payroll");
    } catch (err) {
      toast.error(errorMessage(err, "Failed to update"));
    }
  };

  if (loading) return <div className="p-6 space-y-3">{[...Array(6)].map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}</div>;

  return (
    <div className="p-6 max-w-xl">
      <PageHeader
        title={`Edit — ${name}`}
        description="Update employee payroll details"
      />

      <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-5 bg-white rounded-xl border border-gray-200 p-6">
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1.5">
            <Label>Position</Label>
            <Input placeholder="e.g. Store Manager" {...register("position")} />
          </div>
          <div className="space-y-1.5">
            <Label>Department</Label>
            <Input placeholder="e.g. Operations" {...register("department")} />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1.5">
            <Label>Salary amount</Label>
            <Input type="number" step="0.01" placeholder="0.00" {...register("salary")} />
            {errors.salary && <p className="text-xs text-red-600">{errors.salary.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label>Pay period</Label>
            <select {...register("salaryType")} className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm bg-white">
              <option value="">— Select —</option>
              {SALARY_TYPES.map((t) => (
                <option key={t} value={t}>{t.charAt(0) + t.slice(1).toLowerCase()}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1.5">
            <Label>Start date</Label>
            <Input type="date" {...register("startDate")} />
          </div>
          <div className="space-y-1.5">
            <Label>End date</Label>
            <Input type="date" {...register("endDate")} />
          </div>
        </div>

        <div className="flex gap-3 pt-2">
          <Button type="button" variant="outline" onClick={() => router.push("/payroll")}>Cancel</Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Saving…" : "Save Changes"}
          </Button>
        </div>
      </form>
    </div>
  );
}
