import type { Metadata } from "next";
import { Geist, Geist_Mono, Plus_Jakarta_Sans } from "next/font/google";
import { MainProvider } from "@/context/MainProvider";
import "./globals.css";

const geistSans = Geist({ variable: "--font-geist-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });
const jakarta = Plus_Jakarta_Sans({ variable: "--font-jakarta", subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Inventory",
  description: "Universal multi-company inventory management",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${jakarta.variable} ${geistSans.variable} ${geistMono.variable} h-full`}>
      <body className="min-h-full antialiased" suppressHydrationWarning>
        <MainProvider>{children}</MainProvider>
      </body>
    </html>
  );
}
