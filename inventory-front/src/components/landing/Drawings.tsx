/**
 * Line drawings for the landing page, in the blueprint style of the sign-in page. Every line
 * uses pathLength=1 so .bp-draw can draw it in; inside a Slide they wait until it is in view.
 */

const line = { fill: "none", stroke: "currentColor", strokeLinecap: "round" as const, strokeLinejoin: "round" as const };
const at = (s: number) => ({ animationDelay: `${s}s` });

/** The cover: a dimensioned shipping box with its label and a call-out. */
export function HeroBox({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 400 370" className={className} aria-hidden="true">
      <defs>
        <marker id="lp-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <path d="M0 1 L9 5 L0 9" fill="none" stroke="currentColor" strokeWidth="1.4" />
        </marker>
      </defs>
      <g className="bp-fade" style={at(0.1)} opacity="0.35">
        <path d="M217 30 V356" {...line} strokeWidth="0.8" strokeDasharray="14 4 2 4" />
        <path d="M66 212 H370" {...line} strokeWidth="0.8" strokeDasharray="14 4 2 4" />
      </g>
      <path className="bp-draw" style={{ ...at(0.2), animationDuration: "1.6s" }} pathLength={1}
        d="M200 80 L329.9 155 L329.9 275 L234.6 330 L104.7 255 L104.7 135 Z" {...line} strokeWidth="1.8" />
      <path className="bp-draw" style={at(1.2)} pathLength={1} d="M104.7 135 L234.6 210 L329.9 155 M234.6 210 V330" {...line} strokeWidth="1.5" />
      <path className="bp-draw" style={at(1.6)} pathLength={1} d="M145.7 111.4 L275.6 186.4 V306.4" {...line} strokeWidth="1.1" />
      <path className="bp-draw" style={at(1.7)} pathLength={1} d="M159.1 103.6 L289 178.6 V298.6" {...line} strokeWidth="1.1" />
      <path className="bp-draw" style={at(1.9)} pathLength={1} d="M124.2 172 L182.7 205.8 V249.8 L124.2 216 Z" {...line} strokeWidth="1.1" />
      {[0.1, 0.18, 0.24, 0.34, 0.42, 0.5, 0.58, 0.68, 0.76, 0.86].map((t, i) => {
        const x = 124.2 + 58.5 * t, y = 172 + 33.75 * t;
        return <path key={t} className="bp-bar" style={at(2.1 + i * 0.04)} d={`M${x} ${y + 8} V${y + 26}`} {...line} strokeWidth={i % 3 === 0 ? 2 : 0.9} />;
      })}
      <g className="bp-fade" style={at(2.4)}>
        <path d="M98 135 H70 M98 255 H70" {...line} strokeWidth="0.7" opacity="0.6" />
        <path d="M78 140 V250" {...line} strokeWidth="0.9" markerStart="url(#lp-arrow)" markerEnd="url(#lp-arrow)" />
        <text x="60" y="199" textAnchor="middle" fontSize="10" letterSpacing="1" fill="currentColor" transform="rotate(-90 60 195)">250 MM</text>
        <path d="M250.5 343.5 L335.5 294.5" {...line} strokeWidth="0.9" markerStart="url(#lp-arrow)" markerEnd="url(#lp-arrow)" />
        <text x="306" y="337" textAnchor="middle" fontSize="10" letterSpacing="1" fill="currentColor" transform="rotate(-30 306 333)">400 MM</text>
        <path d="M97 268.5 L219.5 339.5" {...line} strokeWidth="0.9" markerStart="url(#lp-arrow)" markerEnd="url(#lp-arrow)" />
        <text x="146" y="324" textAnchor="middle" fontSize="10" letterSpacing="1" fill="currentColor" transform="rotate(30 146 320)">300 MM</text>
      </g>
      <g className="bp-fade" style={at(2.7)}>
        <circle cx="152" cy="200" r="2.4" fill="currentColor" />
        <path d="M152 200 L96 72 H36" {...line} strokeWidth="0.8" />
        <text x="36" y="64" fontSize="10" letterSpacing="1" fill="currentColor">SKU BX-0001 · 240 ON HAND</text>
      </g>
    </svg>
  );
}

/** "One system": six parts of the app as an exploded stack of slabs, each with a numbered tag. */
export const MODULES = [
  { n: "01", name: "Point of sale" },
  { n: "02", name: "Stock & batches" },
  { n: "03", name: "Transfers" },
  { n: "04", name: "Purchases" },
  { n: "05", name: "The books" },
  { n: "06", name: "Team & roles" },
];

export function LayerStack({ className }: { className?: string }) {
  const cx = 200, a = 140, b = 70, t = 12, gap = 50, top = 86;
  // Bottom slab first, so each slab above covers the one below (slabs are filled with the page colour).
  const slabs = MODULES.map((m, i) => ({ ...m, i, cy: top + i * gap })).reverse();
  return (
    <svg viewBox="0 0 560 470" className={className} aria-hidden="true">
      {slabs.map(({ n, name, i, cy }) => {
        const left = `${cx - a} ${cy}`, topPt = `${cx} ${cy - b}`, right = `${cx + a} ${cy}`, bottom = `${cx} ${cy + b}`;
        // Tags sit to the right of the stack, each joined to its slab's corner.
        const anchorX = cx + a;
        const tagX = 410;
        const accent = i === 0;
        const d0 = 0.2 + (5 - i) * 0.22;
        return (
          <g key={n}>
            {/* slab: sides then top face */}
            <path d={`M${cx - a} ${cy} V${cy + t} L${cx} ${cy + b + t} L${cx + a} ${cy + t} V${cy} L${cx} ${cy + b} Z`}
              fill="var(--lp-ink)" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" className="bp-fade" style={at(d0)} />
            <path d={`M${left} L${topPt} L${right} L${bottom} Z`} fill={accent ? "color-mix(in srgb, var(--brand-blue) 30%, var(--lp-ink))" : "var(--lp-ink)"}
              stroke={accent ? "var(--brand-blue)" : "currentColor"} strokeWidth="1.4" strokeLinejoin="round" className="bp-fade" style={at(d0)} />
            {/* a few details on each top face */}
            <path className="bp-draw" style={at(d0 + 0.3)} pathLength={1}
              d={`M${cx - a * 0.55} ${cy - b * 0.05} L${cx + a * 0.05} ${cy - b * 0.65} M${cx - a * 0.25} ${cy + b * 0.25} L${cx + a * 0.4} ${cy - b * 0.4}`}
              {...line} strokeWidth="0.8" opacity="0.5" />
            {/* leader line + tag */}
            <path className="bp-draw" style={at(d0 + 0.5)} pathLength={1}
              d={`M${anchorX} ${cy} H${anchorX + 30} L${tagX - 8} ${cy - 24}`}
              {...line} strokeWidth="0.8" opacity="0.8" />
            <circle cx={anchorX} cy={cy} r="2.6" fill="currentColor" className="bp-fade" style={at(d0 + 0.5)} />
            <g className="bp-fade" style={at(d0 + 0.7)}>
              <rect x={tagX - 4} y={cy - 38} width="24" height="15" fill="var(--brand-blue)" />
              <text x={tagX + 8} y={cy - 27} textAnchor="middle" fontSize="10" fill="#fff">{n}</text>
              <text x={tagX + 26} y={cy - 27} fontSize="10.5" letterSpacing="1" fill="currentColor">{name.toUpperCase()}</text>
            </g>
          </g>
        );
      })}
    </svg>
  );
}
