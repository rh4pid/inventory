"use client";
import { PageHeader } from "@/components/shared/PageHeader";
import { MyCompanies } from "@/components/company/MyCompanies";
import { PendingInvites } from "@/components/company/PendingInvites";

export default function CompaniesPage() {
  return (
    <div className="max-w-3xl space-y-5">
      <PageHeader title="My companies" description="Switch between the companies you work in, or start your own" />
      <PendingInvites />
      <MyCompanies />
    </div>
  );
}
