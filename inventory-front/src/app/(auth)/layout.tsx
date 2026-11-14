export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return <div className="app-canvas relative min-h-dvh overflow-hidden">{children}</div>;
}
