#!/usr/bin/env python3
"""Generate Stratos VPN launcher icons (legacy PNG fallbacks + store icon).

Brand mark: a minimal "planet + orbit" — the stratosphere.
Palette: deep space indigo background, cyan->indigo planet, cyan orbit ring.
"""
import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "..", "V2rayNG", "app", "src", "main", "res")

# ---------------- palette ----------------
BG_TOP = (13, 18, 48)        # 0D1230
BG_BOTTOM = (5, 7, 20)       # 050714
CYAN = (34, 211, 238)        # 22D3EE
SKY = (56, 189, 248)         # 38BDF8
INDIGO = (99, 102, 241)      # 6366F1
WHITE = (240, 244, 255)


def vgrad(size, top, bottom):
    """Vertical linear gradient image."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        t = y / (size - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(size):
            px[x, y] = c
    return img


def radial_grad(size, inner, outer):
    """Radial gradient: inner at center -> outer at corners."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    cx = cy = (size - 1) / 2
    maxd = math.hypot(cx, cy)
    for y in range(size):
        for x in range(size):
            t = min(1.0, math.hypot(x - cx, y - cy) / maxd)
            t = t ** 1.1
            px[x, y] = tuple(int(inner[i] + (outer[i] - inner[i]) * t) for i in range(3))
    return img


def diag_grad(size, c1, c2):
    """Diagonal (top-left -> bottom-right) gradient used for the planet disc."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            px[x, y] = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
    return img


def circle_mask(size, cx, cy, r):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    return m


def draw_mark(size, angle=-22, ring=True, stars=True):
    """Draw the Stratos mark on a transparent canvas of `size` (render at 4x)."""
    S = size * 4
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cx = cy = S / 2
    planet_r = int(S * 0.185)
    ring_rx = int(S * 0.335)
    ring_ry = int(S * 0.125)
    ring_w = max(4, int(S * 0.022))

    # --- orbit ring (back half) on its own layer, rotated ---
    ring_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    rd = ImageDraw.Draw(ring_layer)
    bbox = [cx - ring_rx, cy - ring_ry, cx + ring_rx, cy + ring_ry]
    if ring:
        rd.ellipse(bbox, outline=CYAN + (255,), width=ring_w)
    ring_layer = ring_layer.rotate(angle, resample=Image.BICUBIC, center=(cx, cy))
    img.alpha_composite(ring_layer)

    # --- planet disc with diagonal gradient ---
    planet = diag_grad(planet_r * 2, CYAN, INDIGO).convert("RGBA")
    mask = circle_mask(planet_r * 2, planet_r, planet_r, planet_r - 1)
    img.paste(planet, (int(cx - planet_r), int(cy - planet_r)), mask)

    # subtle highlight on planet (top-left)
    from PIL import ImageFilter
    hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    hl_r = int(planet_r * 0.55)
    hd.ellipse(
        [cx - planet_r * 0.75, cy - planet_r * 0.85, cx - planet_r * 0.75 + 2 * hl_r, cy - planet_r * 0.85 + 2 * hl_r],
        fill=(255, 255, 255, 48),
    )
    hl = hl.filter(ImageFilter.GaussianBlur(int(S * 0.012)))
    hl.putalpha(Image.composite(hl.getchannel("A"), Image.new("L", (S, S), 0), circle_mask(S, cx, cy, planet_r - 1)))
    img.alpha_composite(hl)

    # --- orbit ring (front half) ---
    ring_front = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    rd2 = ImageDraw.Draw(ring_front)
    if ring:
        rd2.arc(bbox, start=0, end=180, fill=SKY + (255,), width=ring_w)
    ring_front = ring_front.rotate(angle, resample=Image.BICUBIC, center=(cx, cy))
    img.alpha_composite(ring_front)

    # --- satellite dot on the ring (compute rotated ellipse point) ---
    t = math.radians(158)
    ex = cx + ring_rx * math.cos(t)
    ey = cy + ring_ry * math.sin(t)
    a = math.radians(angle)
    rx = cx + (ex - cx) * math.cos(a) - (ey - cy) * math.sin(a)
    ry = cy + (ex - cx) * math.sin(a) + (ey - cy) * math.cos(a)
    dot_r = max(5, int(S * 0.036))
    dd = ImageDraw.Draw(img)
    dd.ellipse([rx - dot_r, ry - dot_r, rx + dot_r, ry + dot_r], fill=WHITE + (255,))
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([rx - dot_r * 2.2, ry - dot_r * 2.2, rx + dot_r * 2.2, ry + dot_r * 2.2], fill=CYAN + (90,))
    img = Image.alpha_composite(glow, img)

    # --- tiny stars ---
    if stars:
        sd = ImageDraw.Draw(img)
        for (sx, sy, sr) in [(0.20, 0.26, 0.012), (0.80, 0.22, 0.009), (0.72, 0.80, 0.010), (0.24, 0.74, 0.008)]:
            px, py, pr = sx * S, sy * S, max(2, sr * S)
            sd.ellipse([px - pr, py - pr, px + pr, py + pr], fill=(200, 220, 255, 200))

    return img.resize((size, size), Image.LANCZOS)


def compose_full_icon(size):
    """Square legacy icon: space background + mark."""
    bg = radial_grad(size * 4, BG_TOP, BG_BOTTOM).convert("RGBA")
    mark = draw_mark(size * 4)
    mark = mark.resize((size * 4, size * 4), Image.LANCZOS)
    bg.alpha_composite(mark)
    return bg.resize((size, size), Image.LANCZOS)


def compose_round_icon(size):
    sq = compose_full_icon(size)
    m = circle_mask(size, size / 2, size / 2, size / 2 - 1)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(sq, (0, 0), m)
    return out


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

def main():
    for dpi, px in DENSITIES.items():
        d = os.path.join(RES, f"mipmap-{dpi}")
        os.makedirs(d, exist_ok=True)
        compose_full_icon(px).save(os.path.join(d, "ic_launcher.png"))
        compose_round_icon(px).save(os.path.join(d, "ic_launcher_round.png"))
        # drop stale adaptive-foreground images; vector drawable replaces them
        stale = os.path.join(d, "ic_launcher_foreground.png")
        if os.path.exists(stale):
            os.remove(stale)
        print("wrote", d)

    # play store icon
    store_dir = os.path.join(ROOT, "..", "fastlane", "metadata", "android", "en-US", "images")
    os.makedirs(store_dir, exist_ok=True)
    compose_full_icon(512).save(os.path.join(store_dir, "icon.png"))
    print("wrote store icon")

if __name__ == "__main__":
    main()
