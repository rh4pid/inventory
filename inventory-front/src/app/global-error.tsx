"use client"; // Error boundaries must be Client Components

/**
 * Last resort when the root layout itself fails. Renders its own document, so it can't use the
 * app's stylesheet: plain inline styles in the default (Market) colours, no black.
 */
export default function GlobalError({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  return (
    <html lang="en">
      <body style={{ margin: 0, minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center",
        background: "#eef1f5", color: "#1c2541", fontFamily: "system-ui, sans-serif" }}>
        <title>Something went wrong · Inventory</title>
        <div style={{ maxWidth: 420, margin: 16, padding: 32, borderRadius: 32, background: "#ffffff", textAlign: "center",
          boxShadow: "0 12px 40px -12px rgba(28,37,65,.25)" }}>
          <h1 style={{ fontSize: 18, margin: 0 }}>Inventory couldn&apos;t load</h1>
          <p style={{ fontSize: 14, color: "#6e7690", lineHeight: 1.5 }}>
            Something went wrong while starting the app. Your data is safe.
            {error.digest ? ` Reference: ${error.digest}` : ""}
          </p>
          <button onClick={() => retry()} style={{ marginTop: 8, height: 40, padding: "0 18px", border: 0, borderRadius: 12,
            background: "#3563e9", color: "#ffffff", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            Try again
          </button>
        </div>
      </body>
    </html>
  );
}
