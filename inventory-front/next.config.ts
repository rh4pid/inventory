import type { NextConfig } from "next";

// Where the Spring backend lives. The browser calls /api/... on the frontend's own domain and
// Next forwards it here, so the refresh cookie stays first-party (proxy.ts can see it) and no CORS is needed.
// Read at build time: pass it as a build argument / build env var.
const backendUrl = (process.env.BACKEND_URL ?? "http://localhost:5001").replace(/\/+$/, "");

const nextConfig: NextConfig = {
  reactCompiler: true,
  // Self-contained server in .next/standalone, used by the Docker image.
  output: "standalone",
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/:path*` }];
  },
};

export default nextConfig;
