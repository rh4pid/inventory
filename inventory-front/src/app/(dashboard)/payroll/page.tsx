"use client";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Plus, UserX, Pencil } from "lucide-react";
import { employees as employeesApi } from "@/lib/api";
import { useBusiness } from "@/context/BusinessContext";
import type { Employee } from "@/Types";
import { formatCurrency, formatDate } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/EmptyState";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { Skeleton } from "@/components/ui/skeleton";

export default function PayrollPage() {
  const router = useRouter();
  const { activeBusiness } = useBusiness();
  const bizId = activeBusiness?.businessId ?? 0;

  const [data, setData] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(true);
  const [terminateId, setTerminateId] = useState<number | null>(null);
  const [terminating, setTerminating] = useState(false);

  const load = () => {
    if (!bizId) return;
    setLoading(true);
    employeesApi.list(bizId, { size: 100 })
      .then((r) => setData(r.content))
      .catch(() => toast.error("Failed to load employees"))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [bizId]);

  const handleTerminate = async () => {
    if (!terminateId) return;
    setTerminating(true);
    try {
      await employeesApi.terminate(bizId, terminateId);
      toast.success("Employee terminated");
      setTerminateId(null);
      load();
    } catch (err: any) {
      toast.error(err?.message ?? "Failed to terminate");
    } finally {
      setTerminating(false);
    }
  };

  const salaryPeriod = (e: Employee) => {
    if (!e.salaryType) return "";
    const map: Record<string, string> = { MONTHLY: "/mo", WEEKLY: "/wk", DAILY: "/day", HOURLY: "/hr" };
    return map[e.salaryType] ?? "";
  };

  const isActive = (e: Employee) => !e.endDate || new Date(e.endDate) > new Date();

  const stats = {
    total: data.length,
    active: data.filter(isActive).length,
    monthlyPayroll: data.filter(isActive).reduce((s, e) => {
      if (!e.salary) return s;
      const mult: Record<string, number> = { MONTHLY: 1, WEEKLY: 4.33, DAILY: 22, HOURLY: 160 };
      return s + e.salary * (mult[e.salaryType ?? "MONTHLY"] ?? 1);
    }, 0),
  };

  return (
    <div className="p-6 space-y-6">
      <PageHeader
        title="Payroll & HR"
        description="Manage employees and salary records"
        action={
          <Button onClick={() => router.push("/payroll/new")}>
            <Plus className="h-4 w-4 mr-2" />
            Add Employee
          </Button>
        }
      />

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4">
        {[
          { label: "Total Employees", value: String(stats.total) },
          { label: "Active Employees", value: String(stats.active) },
          { label: "Monthly Payroll", value: formatCurrency(stats.monthlyPayroll) },
        ].map((s) => (
          <div key={s.label} className="rounded-xl border border-gray-200 bg-white p-4">
            <p className="text-sm text-gray-500">{s.label}</p>
            <p className="mt-1 text-2xl font-bold text-gray-900">{s.value}</p>
          </div>
        ))}
      </div>

      {/* Table */}
      <div className="rounded-xl border border-gray-200 bg-white overflow-hidden">
        {loading ? (
          <div className="p-6 space-y-3">
            {[...Array(5)].map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
          </div>
        ) : data.length === 0 ? (
          <EmptyState
            title="No employees yet"
            description="Add your first employee to get started"
            action={<Button onClick={() => router.push("/payroll/new")}><Plus className="h-4 w-4 mr-2" />Add Employee</Button>}
          />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Position</TableHead>
                <TableHead>Department</TableHead>
                <TableHead>Salary</TableHead>
                <TableHead>Start Date</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-24" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((emp) => (
                <TableRow key={emp.id}>
                  <TableCell className="font-medium">
                    {emp.businessUser.user.firstName} {emp.businessUser.user.lastName}
                    <div className="text-xs text-gray-400">{emp.businessUser.user.email}</div>
                  </TableCell>
                  <TableCell>{emp.position ?? "—"}</TableCell>
                  <TableCell>{emp.department ?? "—"}</TableCell>
                  <TableCell>
                    {emp.salary != null ? (
                      <span className="font-semibold">{formatCurrency(emp.salary)}<span className="text-xs text-gray-400">{salaryPeriod(emp)}</span></span>
                    ) : "—"}
                  </TableCell>
                  <TableCell>{emp.startDate ? formatDate(emp.startDate) : "—"}</TableCell>
                  <TableCell>
                    {isActive(emp) ? (
                      <Badge className="bg-green-100 text-green-700 hover:bg-green-100">Active</Badge>
                    ) : (
                      <Badge variant="secondary">Terminated</Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => router.push(`/payroll/${emp.id}/edit`)}
                        className="p-1.5 rounded-md hover:bg-gray-100 text-gray-500 hover:text-gray-700 transition-colors"
                        title="Edit"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      {isActive(emp) && (
                        <button
                          onClick={() => setTerminateId(emp.id)}
                          className="p-1.5 rounded-md hover:bg-red-50 text-gray-400 hover:text-red-600 transition-colors"
                          title="Terminate"
                        >
                          <UserX className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      <ConfirmDialog
        open={terminateId != null}
        onOpenChange={(o) => { if (!o) setTerminateId(null); }}
        title="Terminate Employee"
        description="Are you sure you want to terminate this employee? This will set their end date to today."
        confirmLabel="Terminate"
        variant="destructive"
        loading={terminating}
        onConfirm={handleTerminate}
      />
    </div>
  );
}
