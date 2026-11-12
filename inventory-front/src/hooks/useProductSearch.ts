"use client";
import { useEffect, useRef, useState } from "react";
import { products as productsApi } from "@/lib/api";
import type { Product } from "@/Types";

/**
 * Products matching what the user types, searched on the server (name, SKU or barcode), so a
 * catalogue of any size works. With an empty query it returns the first page.
 */
export function useProductSearch(businessId: number | undefined, query: string, size = 48) {
  const [results, setResults] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const seq = useRef(0);

  useEffect(() => {
    if (!businessId) return;
    const term = query.trim();
    const mine = ++seq.current;
    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const page = await productsApi.list(businessId, { size, search: term || undefined });
        if (mine === seq.current) { setResults(page.content); setTotal(page.totalElements); }
      } catch {
        if (mine === seq.current) { setResults([]); setTotal(0); }
      } finally {
        if (mine === seq.current) setLoading(false);
      }
    }, term ? 250 : 0);
    return () => clearTimeout(timer);
  }, [businessId, query, size]);

  return { results, total, loading };
}
