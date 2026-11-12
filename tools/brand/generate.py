"""Wisteria brand asset generator.

Writes logo.svg, mark.svg, banner.svg, palette.svg and divider.svg into assets/.
All text is converted to outlines, so the SVGs render the same everywhere
(GitHub strips web fonts from README images).

Usage:  pip install fonttools && python3 tools/brand/generate.py [out_dir]
Needs Inter and JetBrains Mono (both SIL OFL 1.1) installed; adjust FONTS if
they live somewhere else on your system.
"""
import sys, math, pathlib
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

OUT = sys.argv[1] if len(sys.argv) > 1 else str(pathlib.Path(__file__).resolve().parents[2] / "assets")
FONTS = {
    "display": "/usr/share/fonts/opentype/inter/InterDisplay-Medium.otf",
    "light": "/usr/share/fonts/opentype/inter/InterDisplay-Light.otf",
    "text": "/usr/share/fonts/opentype/inter/Inter-Regular.otf",
    "semibold": "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf",
    "mono": "/usr/share/fonts/truetype/jetbrains-mono/JetBrainsMono-Regular.ttf",
    "monomed": "/usr/share/fonts/truetype/jetbrains-mono/JetBrainsMono-Medium.ttf",
}
_cache = {}
def font(k):
    if k not in _cache:
        _cache[k] = TTFont(FONTS[k])
    return _cache[k]

def text_path(s, k, size, x=0, y=0, tracking=0.0, anchor="start"):
    """Return (svg path d, width). Text is outlined so rendering is font-independent."""
    f = font(k); gs = f.getGlyphSet(); cmap = f.getBestCmap()
    upm = f["head"].unitsPerEm; sc = size / upm
    adv = []; names = []
    for ch in s:
        g = cmap.get(ord(ch)); names.append(g)
        adv.append((gs[g].width if g else upm * 0.3) * sc + tracking * size)
    width = sum(adv) - tracking * size
    ox = x - (width / 2 if anchor == "middle" else width if anchor == "end" else 0)
    d = []
    for g, a in zip(names, adv):
        if g:
            pen = SVGPathPen(gs, ntos=lambda v: f"{v:.1f}".rstrip("0").rstrip("."))
            gs[g].draw(TransformPen(pen, (sc, 0, 0, -sc, ox, y)))
            c = pen.getCommands()
            if c: d.append(c)
        ox += a
    return " ".join(d), width

# ---- palette ---------------------------------------------------------------
VOID, SURFACE, RAISED, OUTLINE = "#0E0B16", "#17131F", "#211B2E", "#2E2740"
TEXT, MUTED = "#ECE8F5", "#9A92AD"
PETAL, WISTERIA, VIOLET, DEEP = "#E9D8FF", "#B79CFF", "#8B6CFF", "#5B3FD6"

def lerp_hex(a, b, t):
    pa = [int(a[i:i+2], 16) for i in (1, 3, 5)]; pb = [int(b[i:i+2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * t):02X}" for x, y in zip(pa, pb))
def ramp(t):
    stops = [(0, PETAL), (0.38, WISTERIA), (0.72, VIOLET), (1, DEEP)]
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1: return lerp_hex(c0, c1, (t - t0) / (t1 - t0))
    return DEEP

# ---- the mark: a hanging raceme of blossoms on a diamond lattice -------------
#           (row, column offset, scale, kind)
CLUSTER = [
    (0, 0.0, 0.80, "f"),
    (1, -0.5, 1.00, "f"), (1, 0.5, 1.00, "f"),
    (2, -1.0, 0.86, "f"), (2, 0.0, 1.00, "f"), (2, 1.0, 0.86, "f"),
    (3, -0.5, 1.00, "f"), (3, 0.5, 1.00, "f"),
    (4, 0.0, 1.00, "f"), (4, 1.0, 0.70, "o"),
    (5, -0.5, 0.88, "f"), (5, 0.5, 0.94, "f"),
    (6, 0.0, 0.86, "f"),
    (7, -0.5, 0.60, "o"), (7, 0.5, 0.74, "f"),
    (8, 0.0, 0.62, "f"),
    (9, 0.5, 0.48, "f"),
    (10, 0.0, 0.34, "f"),
]
def blossom(cx, cy, half, fill, kind="f", extra=""):
    side = half * math.sqrt(2); rx = side * 0.22
    common = (f'x="{-side/2:.2f}" y="{-side/2:.2f}" width="{side:.2f}" height="{side:.2f}" rx="{rx:.2f}" '
              f'transform="translate({cx:.2f} {cy:.2f}) rotate(45)"')
    if kind == "o":
        sw = half * 0.2
        return f'<rect {common} fill="none" stroke="{fill}" stroke-width="{sw:.2f}"{extra}/>'
    return f'<rect {common} fill="{fill}"{extra}/>'

def mark(cx, top, step, animate=False):
    """Blossoms hanging from (cx, top). `step` = lattice pitch. Returns svg string + bottom y."""
    half = step * 0.43
    rows = max(r for r, *_ in CLUSTER)
    out = []
    for n, (r, c, s, kind) in enumerate(CLUSTER):
        x = cx + c * step; y = top + r * step / 2
        extra = ""
        if animate:
            extra = f' class="b" style="animation-delay:{0.12 * n:.2f}s"'
        out.append(blossom(x, y, half * s, ramp(r / rows), kind, extra))
    return "\n    ".join(out), top + rows * step / 2 + half

def stem(cx, top, step, color=WISTERIA, opacity=0.9):
    # the tip of a branch arching in from the right; the raceme hangs from its end
    sw = step * 0.1
    d = (f"M{cx + step*1.75:.1f} {top - step*1.02:.1f} "
         f"C{cx + step*1.0:.1f} {top - step*1.5:.1f} {cx:.1f} {top - step*1.3:.1f} {cx:.1f} {top - step*0.5:.1f}")
    return f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{sw:.2f}" stroke-linecap="round" opacity="{opacity}"/>'

def write(name, svg):
    with open(f"{OUT}/{name}", "w") as fh: fh.write(svg.strip() + "\n")
    print("wrote", name, len(svg))

# ---- logo.svg (app tile) + mark.svg (transparent) ---------------------------
def logo():
    m, _ = mark(256, 150, 64)
    write("logo.svg", f'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" role="img" aria-labelledby="t">
  <title id="t">Wisteria</title>
  <defs>
    <radialGradient id="glow" cx="50%" cy="42%" r="55%">
      <stop offset="0" stop-color="{VIOLET}" stop-opacity=".38"/>
      <stop offset="1" stop-color="{VIOLET}" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="edge" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{WISTERIA}" stop-opacity=".55"/>
      <stop offset="1" stop-color="{DEEP}" stop-opacity=".12"/>
    </linearGradient>
  </defs>
  <rect width="512" height="512" rx="116" fill="{VOID}"/>
  <rect width="512" height="512" rx="116" fill="url(#glow)"/>
  <rect x="3" y="3" width="506" height="506" rx="113" fill="none" stroke="url(#edge)" stroke-width="3"/>
  <g>
    {stem(256, 150, 64)}
    {m}
  </g>
</svg>''')
    m, _ = mark(256, 150, 64)
    write("mark.svg", f'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="76 40 360 440" role="img" aria-labelledby="t">
  <title id="t">Wisteria mark</title>
  {stem(256, 150, 64)}
  {m}
</svg>''')

# ---- banner.svg (README hero) ------------------------------------------------
def hanging(cx, top, step, opacity, dur, delay):
    m, _ = mark(cx, top, step)
    sw = max(1.2, step * 0.07)
    return f'''<g class="sway" style="transform-origin:{cx}px 0px;animation-duration:{dur}s;animation-delay:{delay}s" opacity="{opacity}">
    <path d="M{cx} 0V{top - step*0.5:.1f}" stroke="{WISTERIA}" stroke-width="{sw:.2f}" stroke-linecap="round" opacity=".7"/>
    {m}
  </g>'''

def banner():
    W, H = 1280, 420
    clusters = [  # cx, top, step, opacity, sway duration, delay
        (62, 86, 24, .34, 9, -2), (150, 52, 36, .95, 7, 0), (250, 104, 22, .5, 8, -4), (334, 44, 15, .26, 10, -1),
        (952, 50, 15, .26, 10, -3), (1030, 96, 22, .5, 8, -5), (1128, 48, 38, .95, 7.5, -1.5), (1222, 92, 25, .36, 9, -6),
    ]
    petals = [  # x, size, duration, delay, drift, color t
        (120, 7, 13, -1, 40, .2), (212, 5, 17, -9, -30, .5), (300, 4, 15, -5, 26, .8), (64, 4, 19, -12, 30, .6),
        (990, 4, 16, -3, -24, .7), (1076, 6, 14, -8, 34, .3), (1170, 5, 18, -13, -36, .5), (1240, 4, 15, -6, -20, .85),
        (420, 3, 21, -10, 22, .7), (880, 3, 20, -16, -22, .6),
    ]
    word, ww = text_path("WISTERIA", "light", 92, W / 2, 222, tracking=0.2, anchor="middle")
    tag, _ = text_path("Blooms where nothing grows.", "text", 23, W / 2, 278, tracking=0.01, anchor="middle")
    cap, cw = text_path("UTILITY CLIENT  ·  FABRIC  ·  MINECRAFT 26.2", "monomed", 13, W / 2, 132, tracking=0.16, anchor="middle")
    foot, _ = text_path("design-first  /  open source  /  GPL-3.0", "mono", 13, W / 2, 352, tracking=0.06, anchor="middle")
    hang = "\n  ".join(hanging(*c) for c in clusters)
    pet = "\n  ".join(
        f'<g class="fall" style="animation-duration:{d}s;animation-delay:{dl}s;--dx:{dx}px">'
        f'<rect x="{x - sz/2}" y="{-sz}" width="{sz}" height="{sz}" rx="{sz*.22:.1f}" fill="{ramp(t)}" transform="rotate(45 {x} {-sz/2})"/></g>'
        for x, sz, d, dl, dx, t in petals)
    write("banner.svg", f'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" role="img" aria-labelledby="t d">
  <title id="t">Wisteria</title>
  <desc id="d">Wisteria — a design-first utility client for Minecraft 26.2 on Fabric. Blooms where nothing grows.</desc>
  <defs>
    <clipPath id="frame"><rect width="{W}" height="{H}" rx="28"/></clipPath>
    <radialGradient id="g1" cx="50%" cy="44%" r="46%">
      <stop offset="0" stop-color="{VIOLET}" stop-opacity=".30"/>
      <stop offset="1" stop-color="{VIOLET}" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="g2" cx="10%" cy="0%" r="42%">
      <stop offset="0" stop-color="{WISTERIA}" stop-opacity=".22"/>
      <stop offset="1" stop-color="{WISTERIA}" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="g3" cx="90%" cy="0%" r="42%">
      <stop offset="0" stop-color="{DEEP}" stop-opacity=".42"/>
      <stop offset="1" stop-color="{DEEP}" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="word" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="{PETAL}"/>
      <stop offset=".55" stop-color="{WISTERIA}"/>
      <stop offset="1" stop-color="{VIOLET}"/>
    </linearGradient>
    <linearGradient id="edge" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{WISTERIA}" stop-opacity=".5"/>
      <stop offset=".5" stop-color="{OUTLINE}" stop-opacity=".9"/>
      <stop offset="1" stop-color="{DEEP}" stop-opacity=".4"/>
    </linearGradient>
    <linearGradient id="rule" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="{WISTERIA}" stop-opacity="0"/>
      <stop offset=".5" stop-color="{WISTERIA}" stop-opacity=".7"/>
      <stop offset="1" stop-color="{WISTERIA}" stop-opacity="0"/>
    </linearGradient>
    <pattern id="lattice" width="44" height="44" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
      <path d="M44 0H0V44" fill="none" stroke="{WISTERIA}" stroke-opacity=".05" stroke-width="1"/>
    </pattern>
    <linearGradient id="fadeY" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#fff" stop-opacity=".9"/><stop offset="1" stop-color="#fff" stop-opacity="0"/>
    </linearGradient>
    <mask id="latticeMask"><rect width="{W}" height="{H}" fill="url(#fadeY)"/></mask>
  </defs>
  <style>
    .sway {{ animation: sway 8s ease-in-out infinite alternate; }}
    .fall {{ animation: fall 16s linear infinite; }}
    @keyframes sway {{ from {{ transform: rotate(-1.6deg); }} to {{ transform: rotate(1.6deg); }} }}
    @keyframes fall {{
      0% {{ transform: translate(0, 0) rotate(0deg); opacity: 0; }}
      8% {{ opacity: .9; }}
      85% {{ opacity: .7; }}
      100% {{ transform: translate(var(--dx), {H + 30}px) rotate(260deg); opacity: 0; }}
    }}
    @media (prefers-reduced-motion: reduce) {{ .sway, .fall {{ animation: none; }} .fall {{ opacity: 0; }} }}
  </style>
  <g clip-path="url(#frame)">
    <rect width="{W}" height="{H}" fill="{VOID}"/>
    <rect width="{W}" height="{H}" fill="url(#lattice)" mask="url(#latticeMask)"/>
    <rect width="{W}" height="{H}" fill="url(#g2)"/>
    <rect width="{W}" height="{H}" fill="url(#g3)"/>
    <rect width="{W}" height="{H}" fill="url(#g1)"/>
  {hang}
  {pet}
    <path d="{cap}" fill="{MUTED}"/>
    <path d="{word}" fill="url(#word)"/>
    <rect x="{W/2 - 150}" y="246" width="300" height="1" fill="url(#rule)"/>
    <path d="{tag}" fill="{TEXT}" opacity=".92"/>
    <path d="{foot}" fill="{MUTED}" opacity=".75"/>
  </g>
  <rect x=".75" y=".75" width="{W - 1.5}" height="{H - 1.5}" rx="27.25" fill="none" stroke="url(#edge)" stroke-width="1.5"/>
</svg>''')

# ---- palette.svg -------------------------------------------------------------
def palette():
    sw = [("Void", VOID), ("Surface", SURFACE), ("Raised", RAISED), ("Outline", OUTLINE), ("Petal", PETAL),
          ("Wisteria", WISTERIA), ("Violet", VIOLET), ("Deep", DEEP), ("Text", TEXT), ("Muted", MUTED)]
    W, pad, gap, cols = 1280, 32, 18, 5
    cw = (W - 2 * pad - gap * (cols - 1)) / cols; ch = 92; rowh = ch + 62
    H = pad * 2 + rowh * 2 + gap - 10
    items = []
    for i, (name, hx) in enumerate(sw):
        x = pad + (i % cols) * (cw + gap); y = pad + (i // cols) * (rowh + gap)
        n, _ = text_path(name, "semibold", 21, x + 4, y + ch + 34)
        h, _ = text_path(hx, "mono", 17, x + cw - 4, y + ch + 33, anchor="end")
        items.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{cw:.1f}" height="{ch}" rx="16" fill="{hx}" stroke="{OUTLINE}" stroke-width="1"/>'
                     f'<path d="{n}" fill="{TEXT}"/><path d="{h}" fill="{MUTED}"/>')
    body = "\n  ".join(items)
    write("palette.svg", f'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" role="img" aria-labelledby="t">
  <title id="t">Wisteria palette: Void {VOID}, Surface {SURFACE}, Raised {RAISED}, Outline {OUTLINE}, Petal {PETAL}, Wisteria {WISTERIA}, Violet {VIOLET}, Deep {DEEP}, Text {TEXT}, Muted {MUTED}</title>
  <rect width="{W}" height="{H}" rx="22" fill="{VOID}"/>
  <rect x=".5" y=".5" width="{W - 1}" height="{H - 1}" rx="21.5" fill="none" stroke="{OUTLINE}"/>
  {body}
</svg>''')

# ---- divider.svg -------------------------------------------------------------
def divider():
    W, H = 1280, 28
    ds = "".join(blossom(W / 2 + dx, H / 2, r, c) for dx, r, c in [(-26, 3.2, DEEP), (-13, 4.4, VIOLET), (0, 6, WISTERIA), (13, 4.4, VIOLET), (26, 3.2, DEEP)])
    write("divider.svg", f'''
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" role="presentation">
  <defs>
    <linearGradient id="l" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{WISTERIA}" stop-opacity="0"/><stop offset="1" stop-color="{WISTERIA}" stop-opacity=".55"/></linearGradient>
    <linearGradient id="r" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{WISTERIA}" stop-opacity=".55"/><stop offset="1" stop-color="{WISTERIA}" stop-opacity="0"/></linearGradient>
  </defs>
  <rect x="200" y="{H/2 - .5}" width="{W/2 - 244}" height="1" fill="url(#l)"/>
  <rect x="{W/2 + 44}" y="{H/2 - .5}" width="{W/2 - 244}" height="1" fill="url(#r)"/>
  {ds}
</svg>''')

logo(); banner(); palette(); divider()
