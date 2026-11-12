export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="app-canvas flex min-h-dvh items-center justify-center px-4 py-10">
      {children}
    </div>
  );
}
