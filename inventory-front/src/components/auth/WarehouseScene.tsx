/**
 * The login intro, redrawn from inv.png in the same flat style and colours: the person pushes a
 * hand truck of boxes to the stack, the top box hops onto the pile, then the login card appears.
 * Animation lives in globals.css (lg-* classes). Add `lg-skip` to the svg to jump to the end.
 *
 * Coordinates: viewBox 1000×480, floor at y=440.
 */

// Palette from inv.png, with the shirt and wheels in the app's blue (boxes stay cardboard).
const C = {
  box: "#f4a261", boxTape: "#db9157", boxLabel: "#f6b98a",
  shirt: "#5b84ef", shirtShade: "#4a72e0", pants: "#343a40", pantsShade: "#2c333a",
  skin: "#f8c8a0", skinShade: "#eab38a", hair: "#2c333a", shoe: "#9cbed3",
  frame: "#2c333a", wheel: "#3563e9", wheelRim: "#2447c7",
  shelf: "#e4edf2", shelfBoard: "#d9e5ec", shelfBox: "#eef3f7", shelfTape: "#cddbe4",
};

function Box({ x, y, w, h }: { x: number; y: number; w: number; h: number }) {
  return (
    <g>
      <rect x={x} y={y} width={w} height={h} rx={3} fill={C.box} />
      <rect x={x + w / 2 - 7} y={y} width={14} height={h * 0.28} fill={C.boxTape} />
      <rect x={x + w * 0.62} y={y + h * 0.58} width={w * 0.24} height={h * 0.2} rx={2} fill={C.boxLabel} />
      <rect x={x} y={y + h - 4} width={w} height={4} rx={2} fill={C.boxTape} opacity={0.35} />
    </g>
  );
}

function ShelfBox({ x, y, w, h }: { x: number; y: number; w: number; h: number }) {
  return (
    <g>
      <rect x={x} y={y} width={w} height={h} rx={3} fill={C.shelfBox} />
      <rect x={x + w / 2 - 5} y={y} width={10} height={h * 0.3} fill={C.shelfTape} />
    </g>
  );
}


function Person() {
  // Upright figure leaning forward 16° about the hip (80, 322); arms reach the truck handle.
  return (
    <g>
      {/* back leg, then back arm, behind the body */}
      <g className="lg-a lg-leg lg-leg-back">
        <rect x={68} y={318} width={22} height={110} rx={10} fill={C.pantsShade} />
        <ellipse cx={84} cy={432} rx={17} ry={8} fill={C.shoe} />
      </g>
      <g className="lg-a lg-bob">
        <g transform="rotate(16 80 322)">
          <path d="M86 240 L112 250 L142 238" stroke={C.skinShade} strokeWidth={12} fill="none" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M86 240 L100 246" stroke={C.shirtShade} strokeWidth={17} fill="none" strokeLinecap="round" />
          {/* torso */}
          <rect x={56} y={222} width={48} height={112} rx={20} fill={C.shirt} />
          <rect x={56} y={300} width={48} height={34} rx={14} fill={C.pants} />
          {/* neck + head */}
          <rect x={74} y={204} width={12} height={22} rx={5} fill={C.skin} />
          <circle cx={82} cy={186} r={22} fill={C.skin} />
          <path d="M60 186 C58 162 78 154 94 160 C104 163 106 172 104 178 C96 170 86 172 80 178 C76 183 72 192 66 196 Z" fill={C.hair} />
          <circle cx={73} cy={190} r={4.5} fill={C.skinShade} />
          <circle cx={95} cy={186} r={1.8} fill={C.hair} />
          {/* front arm */}
          <path d="M90 236 L118 248 L146 232" stroke={C.skin} strokeWidth={13} fill="none" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M90 236 L105 242" stroke={C.shirt} strokeWidth={18} fill="none" strokeLinecap="round" />
        </g>
      </g>
      {/* front leg */}
      <g className="lg-a lg-leg lg-leg-front">
        <rect x={70} y={318} width={22} height={110} rx={10} fill={C.pants} />
        <ellipse cx={86} cy={432} rx={17} ry={8} fill={C.shoe} />
      </g>
    </g>
  );
}

function HandTruck() {
  return (
    <g>
      {/* frame and boxes, tilted back towards the person */}
      <g transform="rotate(-14 222 432)">
        <rect x={218} y={250} width={8} height={186} rx={4} fill={C.frame} />
        <path d="M222 254 L204 238" stroke={C.frame} strokeWidth={8} strokeLinecap="round" />
        <rect x={218} y={428} width={80} height={8} rx={4} fill={C.frame} />
        <Box x={228} y={378} w={66} h={50} />
        <g className="lg-a lg-truckbox">
          <Box x={231} y={328} w={58} h={50} />
        </g>
      </g>
      {/* wheel */}
      <g className="lg-a lg-wheel">
        <circle cx={226} cy={420} r={22} fill={C.wheel} />
        <circle cx={226} cy={420} r={22} fill="none" stroke={C.wheelRim} strokeWidth={4} />
        <circle cx={226} cy={420} r={7} fill="#ffffff" />
        <rect x={224} y={400} width={4} height={10} rx={2} fill={C.wheelRim} />
      </g>
    </g>
  );
}

export function WarehouseScene({ skip }: { skip: boolean }) {
  return (
    <svg
      viewBox="0 0 1000 480"
      className={`h-auto w-full ${skip ? "lg-skip" : ""}`}
      role="img"
      aria-label="A warehouse worker pushes a hand truck of boxes to the stock pile and adds a box to the top"
    >
      {/* floor */}
      <ellipse cx={560} cy={446} rx={460} ry={14} fill="#1c2541" opacity={0.05} />

      {/* shelving behind the stack */}
      <g>
        {[650, 940].map((x) => <rect key={x} x={x} y={120} width={12} height={322} rx={4} fill={C.shelf} />)}
        {[200, 300].map((y) => <rect key={y} x={646} y={y} width={310} height={9} rx={3} fill={C.shelfBoard} />)}
        <ShelfBox x={690} y={156} w={58} h={44} />
        <ShelfBox x={760} y={164} w={48} h={36} />
        <ShelfBox x={700} y={250} w={56} h={50} />
        <ShelfBox x={770} y={262} w={44} h={38} />
        <ShelfBox x={872} y={256} w={54} h={44} />
      </g>

      {/* the stack */}
      <g>
        {[600, 680, 760, 840].map((x) => <Box key={x} x={x} y={388} w={78} h={52} />)}
        {[640, 720, 800].map((x) => <Box key={x} x={x} y={334} w={78} h={52} />)}
        {[680, 760].map((x) => <Box key={x} x={x} y={280} w={78} h={52} />)}
      </g>

      {/* the box that hops from the truck onto the top of the stack */}
      <g className="lg-a lg-hopwrap">
        <g className="lg-a lg-hop">
          <g className="lg-a lg-land">
            <Box x={-29} y={-25} w={58} h={50} />
          </g>
        </g>
        <g className="lg-a lg-dust">
          <circle cx={716} cy={278} r={6} fill="#e4edf2" />
          <circle cx={802} cy={278} r={5} fill="#e4edf2" />
          <circle cx={706} cy={270} r={3.5} fill="#e4edf2" />
          <circle cx={812} cy={270} r={3.5} fill="#e4edf2" />
        </g>
      </g>

      {/* person + hand truck move together */}
      <g className="lg-a lg-mover">
        <ellipse cx={170} cy={442} rx={120} ry={7} fill="#1c2541" opacity={0.06} />
        <Person />
        <HandTruck />
      </g>
    </svg>
  );
}
