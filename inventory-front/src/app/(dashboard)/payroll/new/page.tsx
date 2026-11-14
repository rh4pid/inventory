"use client";
import { errorMessage } from "@/lib/utils";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import type { Resolver } from "react-hook-form";
import { toast } from "sonner";
import { employees as employeesApi } from "@/lib/api";
import { useBusiness } from "@/context/BusinessContext";
import type { BusinessUser, SalaryType } from "@/Types";
import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const schema = z.object({
  businessUserId: z.coerce.number().positive("Select a staff member"),
  position:       z.string().optional(),
  department:     z.string().optional(),
  salary:         z.coerce.number().positive("Must be positive").optional(),
  salaryType:     z.enum(["MONTHLY", "WEEKLY", "DAILY", "HOURLY"]).optional(),
  startDate:      z.string().optional(),
});

type FormData = z.infer<typeof schema>;

const SALARY_TYPES: SalaryType[] = ["MONTHLY", "WEEKLY", "DAILY", "HOURLY"];

export default function NewEmployeePage() {
  const router = useRouter();
  const { activeBusiness } = useBusiness();
  const bizId = activeBusiness?.businessId ?? 0;

  const [staff, setStaff] = useState<BusinessUser[]>([]);
  const [loadingStaff, setLoadingStaff] = useState(true);

  useEffect(() => {
    if (!bizId) return;
    setLoadingStaff(true);
    employeesApi.listStaff(bizId, { size: 100 })
      .then((r) => setStaff(r.content))
      .catch(() => toast.error("Failed to load staff"))
      .finally(() => setLoadingStaff(false));
  }, [bizId]);

  const { register, handleSubmit, formState: { errors, isSubmitting } } =
    useForm<FormData>({ resolver: zodResolver(schema) as Resolver<FormData> });

  const onSubmit = async (data: FormData) => {
    try {
      await employeesApi.create(bizId, {
        businessUserId: data.businessUserId,
        position:   data.position,
        department: data.department,
        salary:     data.salary,
        salaryType: data.salaryType,
        startDate:  data.startDate,
      });
      toast.success("Employee added");
      router.push("/payroll");
    } catch (err) {
      toast.error(errorMessage(err, "Failed to add employee"));
    }
  };

  return (
    <div className="p-6 max-w-xl">
      <PageHeader
        title="Add Employee"
        description="Link a staff member to a payroll record"
      />

      <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-5 bg-white rounded-xl border border-gray-200 p-6">
        {/* Staff member select */}
        <div className="space-y-1.5">
          <Label>Staff member *</Label>
          <select
            {...register("businessUserId")}
            disabled={loadingStaff}
            className="w-full border border-gray-200 rounded-md px-3 py-2 text-sm bg-white"
          >
            <option value="">— Select staff member —</option>
            {staff.map((bu) => (
              <option key={bu.id} value={bu.id}>
                {bu.user.firstName} {bu.user.lastName} ({bu.roles})
              </option>
            ))}
          </select>
          {errors.businessUserId && <p className="text-xs text-red-600">{errors.businessUserId.message}</p>}
        </div>

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

        <div className="space-y-1.5">
          <Label>Start date</Label>
          <Input type="date" {...register("startDate")} />
        </div>

        <div className="flex gap-3 pt-2">
          <Button type="button" variant="outline" onClick={() => router.push("/payroll")}>Cancel</Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Saving…" : "Add Employee"}
          </Button>
        </div>
      </form>
    </div>
  );
}
