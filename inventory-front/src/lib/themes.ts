/**
 * Company colour themes. Same layout and shapes everywhere; each theme swaps the brand pair,
 * the background tint and the soft fills. Pairs were checked with the data-viz palette
 * validator (colour-blind separation, chart contrast) and for readable text on solid fills.
 *
 * Keep the ids in step with SettingsService.THEMES on the backend.
 */
export type ThemeId = "market" | "care" | "fresh" | "depot" | "workshop" | "bistro";

export interface Theme {
  id: ThemeId;
  name: string;
  tagline: string;
  /** Business types this theme is made for (shown as "recommended"). */
  suits: string[];
  vars: {
    brand: string;        // main colour (charts, links, rings)
    brandStrong: string;  // solid buttons and big cards (white text on it)
    brandDeep: string;    // hover / pressed
    brandSoft: string;    // tinted backgrounds
    accent: string;       // second colour (charts, highlights)
    accentDeep: string;
    accentSoft: string;
    onAccent: string;     // text on accent fills
    canvas: string;       // behind the app frame
    frame: string;        // the app frame
    surface2: string;     // wells, inputs
    line: string;         // borders
  };
}

const INK = "#1c2541";

export const THEMES: Theme[] = [
  {
    id: "market", name: "Market", tagline: "Bright blue and orange: lively shop floor",
    suits: ["GENERAL_RETAIL", "OTHER"],
    vars: { brand: "#3563e9", brandStrong: "#3563e9", brandDeep: "#2447c7", brandSoft: "#e8eefd",
      accent: "#f2622e", accentDeep: "#d9481a", accentSoft: "#fde9e1", onAccent: INK,
      canvas: "#ece6df", frame: "#f7f4f0", surface2: "#f1ede8", line: "#e6e0d8" },
  },
  {
    id: "care", name: "Care", tagline: "Clinical teal and soft coral on mint: calm and clean",
    suits: ["PHARMACY"],
    vars: { brand: "#0f8f80", brandStrong: "#0b7d70", brandDeep: "#0a6a5f", brandSoft: "#e1f3f0",
      accent: "#e0603f", accentDeep: "#c44a2b", accentSoft: "#fce8e2", onAccent: INK,
      canvas: "#e2ece9", frame: "#f4f8f7", surface2: "#e9f1ef", line: "#d8e4e1" },
  },
  {
    id: "fresh", name: "Fresh", tagline: "Leaf green and berry: groceries and provisions",
    suits: ["PROVISION_SHOP", "SUPERMARKET"],
    vars: { brand: "#2b8a3e", brandStrong: "#237a35", brandDeep: "#1d672c", brandSoft: "#e3f3e5",
      accent: "#c8478a", accentDeep: "#a8336f", accentSoft: "#f8e4ef", onAccent: "#ffffff",
      canvas: "#e6ebe0", frame: "#f6f8f2", surface2: "#edf1e7", line: "#dce4d3" },
  },
  {
    id: "depot", name: "Depot", tagline: "Steel blue and safety amber: warehouse and distribution",
    suits: ["WAREHOUSE"],
    vars: { brand: "#2562a8", brandStrong: "#2562a8", brandDeep: "#1c4c84", brandSoft: "#e3edf8",
      accent: "#c47f00", accentDeep: "#a36900", accentSoft: "#fbefd6", onAccent: INK,
      canvas: "#e3e6ea", frame: "#f4f6f8", surface2: "#ebeef2", line: "#d8dde4" },
  },
  {
    id: "workshop", name: "Workshop", tagline: "Indigo and tool yellow: hardware and building",
    suits: ["HARDWARE"],
    vars: { brand: "#4f46d8", brandStrong: "#4f46d8", brandDeep: "#3b33b5", brandSoft: "#e9e8fb",
      accent: "#d17d00", accentDeep: "#b06900", accentSoft: "#fcecd4", onAccent: INK,
      canvas: "#e7e5ee", frame: "#f6f5f9", surface2: "#eeecf4", line: "#dedbe8" },
  },
  {
    id: "bistro", name: "Bistro", tagline: "Wine red and warm gold: restaurants and bars",
    suits: ["RESTAURANT"],
    vars: { brand: "#a8324a", brandStrong: "#a8324a", brandDeep: "#87243a", brandSoft: "#f7e4e8",
      accent: "#b9850f", accentDeep: "#9a6d08", accentSoft: "#f8eed3", onAccent: INK,
      canvas: "#ede4df", frame: "#f9f4f1", surface2: "#f3ebe6", line: "#e8dbd3" },
  },
];

export const themeById = (id?: string | null) => THEMES.find((t) => t.id === id);

/** The theme for a business type when the admin hasn't picked one. */
export function defaultThemeFor(businessType?: string | null): Theme {
  return THEMES.find((t) => businessType && t.suits.includes(businessType)) ?? THEMES[0];
}

/** Chosen theme, or the business type's default ("auto"). */
export function resolveTheme(themeId?: string | null, businessType?: string | null): Theme {
  return themeById(themeId) ?? defaultThemeFor(businessType);
}

const VAR_NAMES: Record<keyof Theme["vars"], string> = {
  brand: "--brand-blue", brandStrong: "--brand-strong", brandDeep: "--brand-blue-deep", brandSoft: "--brand-blue-soft",
  accent: "--brand-orange", accentDeep: "--brand-orange-deep", accentSoft: "--brand-orange-soft", onAccent: "--on-accent",
  canvas: "--canvas", frame: "--frame", surface2: "--surface-2", line: "--line",
};

/** Puts the theme's colours on the page (CSS variables on <html>). */
export function applyTheme(theme: Theme) {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  (Object.keys(VAR_NAMES) as (keyof Theme["vars"])[]).forEach((k) => root.style.setProperty(VAR_NAMES[k], theme.vars[k]));
  root.dataset.theme = theme.id;
}

/** Back to the stylesheet defaults (Market): sign-in page, admin console. */
export function clearTheme() {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  Object.values(VAR_NAMES).forEach((v) => root.style.removeProperty(v));
  delete root.dataset.theme;
}
