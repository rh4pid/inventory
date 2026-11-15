/**
 * Company colour themes. Same layout and shapes everywhere; each theme swaps the brand pair,
 * the background tint and the soft fills. Pairs were checked with the data-viz palette
 * validator (colour-blind separation, chart contrast) and for readable text on solid fills.
 *
 * Keep the ids in step with SettingsService.THEMES on the backend.
 */
export type ThemeId = "market" | "care" | "fresh" | "depot" | "workshop" | "bistro"
  | "ocean" | "sunset" | "forest" | "slate" | "orchid" | "clay" | "boutique";

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
  {
    id: "ocean", name: "Ocean", tagline: "Deep sea blue and coral: cool and confident",
    suits: [],
    vars: { brand: "#0e6ba8", brandStrong: "#0b5d93", brandDeep: "#094b77", brandSoft: "#e2f0f9",
      accent: "#e8715a", accentDeep: "#c9573f", accentSoft: "#fde9e4", onAccent: INK,
      canvas: "#e1e8ee", frame: "#f4f7fa", surface2: "#eaf0f5", line: "#d6e0e8" },
  },
  {
    id: "sunset", name: "Sunset", tagline: "Burnt orange and violet: warm and bold",
    suits: [],
    vars: { brand: "#c2410c", brandStrong: "#b13a0a", brandDeep: "#8f2f08", brandSoft: "#fdeee5",
      accent: "#7c3aed", accentDeep: "#6527c9", accentSoft: "#efe7fd", onAccent: "#ffffff",
      canvas: "#efe6df", frame: "#faf5f1", surface2: "#f4ece6", line: "#e9ddd3" },
  },
  {
    id: "forest", name: "Forest", tagline: "Pine green and harvest gold: natural and steady",
    suits: [],
    vars: { brand: "#1f6f50", brandStrong: "#1a5e44", brandDeep: "#144a36", brandSoft: "#e2f1ea",
      accent: "#b7791f", accentDeep: "#96620f", accentSoft: "#f8eed9", onAccent: INK,
      canvas: "#e2e9e4", frame: "#f4f7f5", surface2: "#eaf0ec", line: "#d7e1da" },
  },
  {
    id: "slate", name: "Slate", tagline: "Charcoal and teal: quiet and corporate",
    suits: [],
    vars: { brand: "#334155", brandStrong: "#334155", brandDeep: "#1e293b", brandSoft: "#e8ecf1",
      accent: "#0e7490", accentDeep: "#0b5e75", accentSoft: "#dff3f7", onAccent: "#ffffff",
      canvas: "#e4e7eb", frame: "#f5f6f8", surface2: "#eceef2", line: "#dadee4" },
  },
  {
    id: "orchid", name: "Orchid", tagline: "Royal purple and jade: premium and fresh",
    suits: [],
    vars: { brand: "#7e3fb8", brandStrong: "#7e3fb8", brandDeep: "#63309a", brandSoft: "#f1e8fa",
      accent: "#0d7f74", accentDeep: "#0a675e", accentSoft: "#dcf2ef", onAccent: "#ffffff",
      canvas: "#ebe5f0", frame: "#f8f5fa", surface2: "#f0ebf5", line: "#e3dbea" },
  },
  {
    id: "clay", name: "Clay", tagline: "Terracotta and olive: earthy and handmade",
    suits: [],
    vars: { brand: "#b4532a", brandStrong: "#a84b24", brandDeep: "#8a3d1d", brandSoft: "#faeae2",
      accent: "#5f7f2a", accentDeep: "#4b6620", accentSoft: "#edf3df", onAccent: "#ffffff",
      canvas: "#eee5dd", frame: "#faf5f0", surface2: "#f3ebe3", line: "#e8dccf" },
  },
  {
    id: "boutique", name: "Boutique", tagline: "Raspberry and indigo: fashion and beauty",
    suits: [],
    vars: { brand: "#be185d", brandStrong: "#be185d", brandDeep: "#9d174d", brandSoft: "#fce7f0",
      accent: "#4338ca", accentDeep: "#3730a3", accentSoft: "#e7e6fb", onAccent: "#ffffff",
      canvas: "#efe4e8", frame: "#faf4f6", surface2: "#f4eaee", line: "#e9dbe1" },
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
