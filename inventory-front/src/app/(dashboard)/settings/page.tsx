"use client";
import Link from "next/link";
import { BookOpen, User, Bell, Building2 } from "lucide-react";
import { useBusiness } from "@/context/BusinessContext";
import { useAuth } from "@/context/AuthProvider";
import { PageHeader } from "@/components/shared/PageHeader";
import { Card, CardContent } from "@/components/ui/card";

const SECTIONS = [
  {
    icon: BookOpen,
    title: "Chart of Accounts",
    description: "Set up and manage your accounting accounts",
    href: "/settings/accounts",
  },
  {
    icon: Building2,
    title: "Business Info",
    description: "Update your business name, logo, and contact details",
    href: "/settings/business",
  },
  {
    icon: User,
    title: "Profile",
    description: "Update your name, email, and password",
    href: "/settings/profile",
  },
  {
    icon: Bell,
    title: "Notifications",
    description: "Configure low-stock alerts and other notifications",
    href: "/settings/notifications",
  },
];

export default function SettingsPage() {
  const { activeBusiness } = useBusiness();
  const { user } = useAuth();

  return (
    <div>
      <PageHeader title="Settings" description={`${activeBusiness?.businessName ?? ""}`} />

      <div className="mb-6 rounded-xl border border-gray-200 bg-white p-5">
        <div className="flex items-center gap-4">
          <div className="h-14 w-14 rounded-full bg-blue-600 flex items-center justify-center text-white text-xl font-bold">
            {user?.firstName?.[0]}{user?.lastName?.[0]}
          </div>
          <div>
            <p className="font-semibold text-gray-900">{user?.firstName} {user?.lastName}</p>
            <p className="text-sm text-gray-500">{user?.email}</p>
            <p className="text-xs text-blue-600 mt-0.5">{activeBusiness?.role}</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {SECTIONS.map((s) => (
          <Link key={s.href} href={s.href}>
            <Card className="hover:border-blue-300 hover:shadow-md transition-all cursor-pointer">
              <CardContent className="flex items-center gap-4 p-5">
                <div className="h-11 w-11 rounded-lg bg-blue-50 flex items-center justify-center text-blue-600 shrink-0">
                  <s.icon className="h-5 w-5" />
                </div>
                <div>
                  <p className="font-medium text-sm text-gray-900">{s.title}</p>
                  <p className="text-xs text-gray-500 mt-0.5">{s.description}</p>
                </div>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
